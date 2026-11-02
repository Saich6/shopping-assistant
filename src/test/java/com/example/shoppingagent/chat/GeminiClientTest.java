package com.example.shoppingagent.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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

    MockRestServiceServer server;
    GeminiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GeminiClient(builder.build(), new GeminiProperties("test-key", "test-model", BASE));
    }

    @Test
    void returnsTextFromGeminiResponse() {
        server.expect(requestTo(BASE + "/models/test-model:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("hello"))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").exists())
                .andRespond(withSuccess("""
                    {"candidates":[{"content":{"parts":[{"text":"Hi there!"}]}}]}
                    """, MediaType.APPLICATION_JSON));

        assertThat(client.generate("hello")).isEqualTo("Hi there!");
        server.verify();
    }

    @Test
    void failsClearlyWhenKeyIsMissing() {
        GeminiClient noKey = new GeminiClient(RestClient.create(), new GeminiProperties("", "m", BASE));
        assertThatThrownBy(() -> noKey.generate("hi"))
                .isInstanceOfSatisfying(GeminiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void mapsGeminiErrorStatusToBadGateway() {
        server.expect(requestTo(BASE + "/models/test-model:generateContent"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.generate("hi"))
                .isInstanceOfSatisfying(GeminiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(e.getMessage()).contains("429");
                });
    }

    @Test
    void emptyAnswerIsAnError() {
        server.expect(requestTo(BASE + "/models/test-model:generateContent"))
                .andRespond(withSuccess("{\"candidates\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generate("hi")).isInstanceOf(GeminiException.class);
    }
}
