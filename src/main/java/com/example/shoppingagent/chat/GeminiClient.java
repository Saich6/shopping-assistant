package com.example.shoppingagent.chat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

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
            String detail = errorDetail(e);
            log.warn("Gemini call failed: HTTP {} {}", e.getStatusCode().value(), detail);
            throw new GeminiException(HttpStatus.BAD_GATEWAY,
                    "Gemini returned HTTP " + e.getStatusCode().value() + (detail.isEmpty() ? "" : ": " + detail), e);
        } catch (RestClientException e) {
            // Network problem or timeout.
            log.warn("Could not reach Gemini", e);
            throw new GeminiException(HttpStatus.BAD_GATEWAY,
                    "Could not reach Gemini: " + e.getMessage(), e);
        }

        String text = response == null ? null : response.path("candidates").path(0)
                .path("content").path("parts").path(0).path("text").asText(null);
        if (text == null || text.isBlank()) {
            throw new GeminiException(HttpStatus.BAD_GATEWAY, "Gemini returned an empty answer");
        }
        return text;
    }

    /** Pulls error.message out of Gemini's JSON error body, e.g. "API key not valid". */
    private static String errorDetail(RestClientResponseException e) {
        try {
            JsonNode body = e.getResponseBodyAs(JsonNode.class);
            String msg = body == null ? "" : body.path("error").path("message").asText("");
            return msg.length() > 300 ? msg.substring(0, 300) : msg;
        } catch (RuntimeException ignored) {
            return "";
        }
    }
}
