package com.example.shoppingagent.chat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Calls the Gemini generateContent REST API. */
@Component
public class GeminiClient {

    static final String SYSTEM_PROMPT = """
        You are the Shopping Assistant for an online warehouse retailer.
        Help customers find products, understand offers and learn about site features.
        Be concise and friendly. If you do not know something, say so instead of guessing.
        """;

    private final RestClient restClient;
    private final GeminiProperties props;

    public GeminiClient(RestClient geminiRestClient, GeminiProperties props) {
        this.restClient = geminiRestClient;
        this.props = props;
    }

    public String generate(String userMessage) {
        if (props.apiKey() == null || props.apiKey().isBlank()) {
            throw new GeminiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "GEMINI_API_KEY is not set. Set the environment variable and restart the app.");
        }

        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", SYSTEM_PROMPT))),
                "contents", List.of(Map.of("role", "user",
                        "parts", List.of(Map.of("text", userMessage)))));

        JsonNode response;
        try {
            response = restClient.post()
                    .uri("/models/{model}:generateContent", props.model())
                    .header("x-goog-api-key", props.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            // Gemini answered with an error status (bad key, quota, bad model name...).
            throw new GeminiException(HttpStatus.BAD_GATEWAY,
                    "Gemini returned HTTP " + e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            // Network problem or timeout.
            throw new GeminiException(HttpStatus.BAD_GATEWAY, "Could not reach Gemini", e);
        }

        String text = response == null ? null : response.path("candidates").path(0)
                .path("content").path("parts").path(0).path("text").asText(null);
        if (text == null || text.isBlank()) {
            throw new GeminiException(HttpStatus.BAD_GATEWAY, "Gemini returned an empty answer");
        }
        return text;
    }
}
