package com.example.shoppingagent.chat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
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
        Rules:
        - For ANY question about products, prices, stock or offers, call the search_products tool
          and answer ONLY from its results. Never invent products, prices or discounts.
        - If the tool returns no products, say you could not find a match and suggest a broader search.
        - When a product is on offer, show the regular price, the discount percent and the sale price.
        - Be concise and friendly. If you do not know something, say so instead of guessing.
        """;

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final RestClient restClient;
    private final GeminiProperties props;

    public GeminiClient(RestClient geminiRestClient, GeminiProperties props) {
        this.restClient = geminiRestClient;
        this.props = props;
    }

    /**
     * Makes ONE call to Gemini with the conversation so far and returns the model's reply
     * (the candidate "content": a role plus parts that are text and/or function calls).
     */
    public JsonNode generateContent(List<JsonNode> contents, List<Map<String, Object>> tools) {
        if (props.apiKey() == null || props.apiKey().isBlank()) {
            throw new GeminiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "GEMINI_API_KEY is not set. Set the environment variable and restart the app.");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", SYSTEM_PROMPT))));
        body.put("contents", contents);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
        }

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

        JsonNode content = response == null ? null : response.path("candidates").path(0).path("content");
        if (content == null || content.isMissingNode() || content.isNull()) {
            throw new GeminiException(HttpStatus.BAD_GATEWAY, "Gemini returned an empty answer");
        }
        return content;
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
