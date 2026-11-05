package com.example.shoppingagent.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.shoppingagent.catalog.CatalogService;
import com.example.shoppingagent.catalog.Product;
import com.example.shoppingagent.conversation.ConversationProperties;
import com.example.shoppingagent.conversation.InMemoryConversationStore;
import com.example.shoppingagent.websearch.SerpApiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Tests the agent loop with a fake Gemini server and a fake catalog. */
class AgentServiceTest {
    static final String BASE = "https://example.test/v1beta";
    static final String URL = BASE + "/models/m:generateContent";       // main model
    static final String FALLBACK_URL = BASE + "/models/fb:generateContent"; // fallback model

    static final String TOOL_CALL = """
        {"candidates":[{"content":{"role":"model","parts":[
          {"functionCall":{"name":"search_products","args":{"query":"tv","only_offers":true}}}]}}]}
        """;
    static final String FINAL_TEXT = """
        {"candidates":[{"content":{"role":"model","parts":[{"text":"The Samsung TV is 15% off."}]}}]}
        """;

    MockRestServiceServer server;
    CatalogService catalog = mock(CatalogService.class);
    SerpApiClient serpApi = mock(SerpApiClient.class);
    AgentService agent;

    static InMemoryConversationStore newStore() {
        return new InMemoryConversationStore(new ConversationProperties(10, 30, 100), Clock.systemUTC());
    }

    static final String SID = "11111111-1111-1111-1111-111111111111";

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        GeminiProperties props = new GeminiProperties("k", "m", "fb", BASE, 3, 0);
        GeminiClient gemini = new GeminiClient(builder.build(), props);
        agent = new AgentService(gemini, new ShoppingTools(catalog, serpApi), new ObjectMapper(), props, newStore());
    }

    @Test
    void answersDirectlyWhenNoToolIsNeeded() {
        server.expect(requestTo(URL)).andRespond(withSuccess(FINAL_TEXT, MediaType.APPLICATION_JSON));

        assertThat(agent.chat(SID, "hi")).isEqualTo("The Samsung TV is 15% off.");
        server.verify();
    }

    @Test
    void runsToolThenSendsResultBackToGemini() {
        when(catalog.search("tv", null, null, true)).thenReturn(List.of(
                new Product("Samsung TV", "electronics", "Samsung", "desc", new BigDecimal("899.99"), 15, true)));

        server.expect(requestTo(URL)).andRespond(withSuccess(TOOL_CALL, MediaType.APPLICATION_JSON));
        // 2nd request must contain: [0] user question, [1] model's function call, [2] our tool result
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.contents.length()").value(3))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("Any TVs on offer?"))
                .andExpect(jsonPath("$.contents[1].parts[0].functionCall.name").value("search_products"))
                .andExpect(jsonPath("$.contents[2].parts[0].functionResponse.name").value("search_products"))
                .andExpect(jsonPath("$.contents[2].parts[0].functionResponse.response.products[0].name")
                        .value("Samsung TV"))
                .andExpect(jsonPath("$.contents[2].parts[0].functionResponse.response.products[0].sale_price")
                        .value(764.99))
                .andRespond(withSuccess(FINAL_TEXT, MediaType.APPLICATION_JSON));

        assertThat(agent.chat(SID, "Any TVs on offer?")).isEqualTo("The Samsung TV is 15% off.");

        verify(catalog).search("tv", null, null, true);
        server.verify();
    }

    @Test
    void stopsAfterMaxRoundsIfModelKeepsCallingTools() {
        server.expect(ExpectedCount.times(AgentService.MAX_ROUNDS), requestTo(URL))
                .andRespond(withSuccess(TOOL_CALL, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> agent.chat(SID, "loop forever"))
                .isInstanceOfSatisfying(GeminiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
        server.verify();
    }

    @Test
    void limitsWebSearchesPerQuestion() {
        // The model asks for 4 web searches in one turn; only MAX_WEB_SEARCHES (3) may actually run.
        String fourWebCalls = """
            {"candidates":[{"content":{"role":"model","parts":[
              {"functionCall":{"name":"search_web_products","args":{"query":"a"}}},
              {"functionCall":{"name":"search_web_products","args":{"query":"b"}}},
              {"functionCall":{"name":"search_web_products","args":{"query":"c"}}},
              {"functionCall":{"name":"search_web_products","args":{"query":"d"}}}]}}]}
            """;
        server.expect(requestTo(URL)).andRespond(withSuccess(fourWebCalls, MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.contents[2].parts[3].functionResponse.response.error")
                        .value(org.hamcrest.Matchers.containsString("limit reached")))
                .andRespond(withSuccess(FINAL_TEXT, MediaType.APPLICATION_JSON));

        agent.chat(SID, "compare everything");

        verify(serpApi, org.mockito.Mockito.times(AgentService.MAX_WEB_SEARCHES))
                .searchShopping(org.mockito.ArgumentMatchers.anyString());
        server.verify();
    }

    @Test
    void usesFallbackModelWhenMainModelStaysOverloaded() {
        // Main model: 3 attempts, all 503. Fallback model then answers.
        server.expect(ExpectedCount.times(3), requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(FALLBACK_URL)).andRespond(withSuccess(FINAL_TEXT, MediaType.APPLICATION_JSON));

        assertThat(agent.chat(SID, "hi")).isEqualTo("The Samsung TV is 15% off.");
        server.verify();
    }

    @Test
    void fallbackRestartsTheQuestionInsteadOfMixingModels() {
        when(catalog.search("tv", null, null, true)).thenReturn(List.of());
        // Main model asks for a tool (success), then goes down on round 2.
        server.expect(requestTo(URL)).andRespond(withSuccess(TOOL_CALL, MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.times(3), requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        // The fallback must start clean: ONLY the user's question, none of the main model's turns.
        server.expect(requestTo(FALLBACK_URL))
                .andExpect(jsonPath("$.contents.length()").value(1))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("Any TVs on offer?"))
                .andRespond(withSuccess(FINAL_TEXT, MediaType.APPLICATION_JSON));

        assertThat(agent.chat(SID, "Any TVs on offer?")).isEqualTo("The Samsung TV is 15% off.");
        server.verify();
    }

    @Test
    void friendlyMessageWhenBothModelsAreOverloaded() {
        server.expect(ExpectedCount.times(3), requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(ExpectedCount.times(3), requestTo(FALLBACK_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> agent.chat(SID, "hi"))
                .isInstanceOfSatisfying(GeminiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getMessage()).contains("busy").doesNotContain("HTTP");
                });
        server.verify();
    }

    @Test
    void noFallbackConfiguredMeansFriendlyMessageAfterMainModelFails() {
        GeminiProperties noFallback = new GeminiProperties("k", "m", "", BASE, 3, 0);
        RestClient.Builder b = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer s = MockRestServiceServer.bindTo(b).build();
        AgentService noFallbackAgent = new AgentService(new GeminiClient(b.build(), noFallback),
                new ShoppingTools(catalog, serpApi), new ObjectMapper(), noFallback, newStore());
        s.expect(ExpectedCount.times(3), requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> noFallbackAgent.chat(SID, "hi"))
                .isInstanceOf(GeminiException.class).hasMessageContaining("busy");
        s.verify();
    }

    @Test
    void badRequestDoesNotTriggerFallback() {
        // A 400 is OUR mistake; the fallback model would fail the same way, so no second model is tried.
        server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> agent.chat(SID, "hi")).isInstanceOf(GeminiException.class);
        server.verify(); // no call to FALLBACK_URL was expected, so one would have failed the test
    }

    // NOTE: the mock server needs ALL expectations declared before the first request is made.

    @Test
    void followUpQuestionIncludesEarlierTurns() {
        server.expect(requestTo(URL)).andRespond(withSuccess(FINAL_TEXT, MediaType.APPLICATION_JSON));
        // Second request in the same session must carry: question 1, answer 1, question 2.
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.contents.length()").value(3))
                .andExpect(jsonPath("$.contents[0].role").value("user"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("Show me TVs"))
                .andExpect(jsonPath("$.contents[1].role").value("model"))
                .andExpect(jsonPath("$.contents[1].parts[0].text").value("The Samsung TV is 15% off."))
                .andExpect(jsonPath("$.contents[2].parts[0].text").value("Which is cheapest?"))
                .andRespond(withSuccess(FINAL_TEXT, MediaType.APPLICATION_JSON));

        agent.chat(SID, "Show me TVs");
        agent.chat(SID, "Which is cheapest?");

        server.verify();
    }

    @Test
    void differentSessionsDoNotShareHistory() {
        server.expect(requestTo(URL)).andRespond(withSuccess(FINAL_TEXT, MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.contents.length()").value(1))   // a new session starts empty
                .andRespond(withSuccess(FINAL_TEXT, MediaType.APPLICATION_JSON));

        agent.chat(SID, "Show me TVs");
        agent.chat("22222222-2222-2222-2222-222222222222", "Hello");

        server.verify();
    }

    @Test
    void failedQuestionIsNotRemembered() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.contents.length()").value(1))   // the failed question left no trace
                .andRespond(withSuccess(FINAL_TEXT, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> agent.chat(SID, "this one fails")).isInstanceOf(GeminiException.class);
        agent.chat(SID, "second try");

        server.verify();
    }

    @Test
    void historyStoresOnlyTextNotToolResults() {
        when(catalog.search("tv", null, null, true)).thenReturn(List.of());
        server.expect(requestTo(URL)).andRespond(withSuccess(TOOL_CALL, MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess(FINAL_TEXT, MediaType.APPLICATION_JSON));
        // Next question: the earlier tool call and its result are NOT replayed, only question + answer + new question.
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.contents.length()").value(3))
                .andRespond(withSuccess(FINAL_TEXT, MediaType.APPLICATION_JSON));

        agent.chat(SID, "Any TVs on offer?");   // used a tool internally
        agent.chat(SID, "thanks");

        server.verify();
    }
}
