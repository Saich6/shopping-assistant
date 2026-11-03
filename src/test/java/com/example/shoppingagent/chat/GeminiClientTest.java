package com.example.shoppingagent.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Tests GeminiClient against a fake server, so no real key or network is needed. */
class GeminiClientTest {
    static final String BASE = "https://example.test/v1beta";

    static final ObjectMapper OM = new ObjectMapper();

    MockRestServiceServer server;
    GeminiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GeminiClient(builder.build(), new GeminiProperties("test-key", "test-model", BASE));
    }

    static List<JsonNode> userMessage(String text) {
        return List.of(OM.createObjectNode().put("role", "user")
                .set("parts", OM.createArrayNode().add(OM.createObjectNode().put("text", text))));
    }

    @Test
    void returnsCandidateContentFromGeminiResponse() {
        server.expect(requestTo(BASE + "/models/test-model:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("hello"))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").exists())
                .andExpect(jsonPath("$.tools[0].functionDeclarations").exists())
                .andRespond(withSuccess("""
                    {"candidates":[{"content":{"parts":[{"text":"Hi there!"}]}}]}
                    """, MediaType.APPLICATION_JSON));

        JsonNode content = client.generateContent(userMessage("hello"),
                List.of(Map.of("functionDeclarations", List.of())));

        assertThat(content.path("parts").path(0).path("text").asText()).isEqualTo("Hi there!");
        server.verify();
    }

    @Test
    void failsClearlyWhenKeyIsMissing() {
        GeminiClient noKey = new GeminiClient(RestClient.create(), new GeminiProperties("", "m", BASE));
        assertThatThrownBy(() -> noKey.generateContent(userMessage("hi"), List.of()))
                .isInstanceOfSatisfying(GeminiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void mapsGeminiErrorStatusToBadGateway() {
        server.expect(requestTo(BASE + "/models/test-model:generateContent"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"Quota exceeded\"}}"));

        assertThatThrownBy(() -> client.generateContent(userMessage("hi"), List.of()))
                .isInstanceOfSatisfying(GeminiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(e.getMessage()).contains("429").contains("Quota exceeded");
                });
    }

    @Test
    void emptyAnswerIsAnError() {
        server.expect(requestTo(BASE + "/models/test-model:generateContent"))
                .andRespond(withSuccess("{\"candidates\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generateContent(userMessage("hi"), List.of())).isInstanceOf(GeminiException.class);
    }
}
