package com.example.shoppingagent.chat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
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
        - For ANY question about products, prices, stock or offers, call a tool and answer ONLY from its
          results. Never invent products, prices or discounts.
        - Try search_products first: it searches OUR store's catalog.
        - Use search_web_products only if our catalog has nothing suitable, or the customer asks for a wider
          selection, other brands or comparisons. Its results come from OTHER retailers: always name the store,
          say they are third-party listings, and never present them as our store's prices or offers.
        - If no tool returns a match, say you could not find one and suggest a broader search.
        - When one of OUR products is on offer, show the regular price, the discount percent and the sale price.
        - Be concise and friendly. If you do not know something, say so instead of guessing.
        """;

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final RestClient restClient;
    private final GeminiProperties props;

    public GeminiClient(@Qualifier("geminiRestClient") RestClient geminiRestClient, GeminiProperties props) {
        this.restClient = geminiRestClient;
        this.props = props;
    }

    /** Calls the main model. See {@link #generateContent(List, List, String)}. */
    public JsonNode generateContent(List<JsonNode> contents, List<Map<String, Object>> tools) {
        return generateContent(contents, tools, props.model());
    }

    /**
     * Asks the given model for the next reply to the conversation so far and returns it
     * (the candidate "content": a role plus parts that are text and/or function calls).
     *
     * Temporary errors (HTTP 429/500/502/503/504) are retried with a growing delay. If every attempt fails,
     * a {@link ModelOverloadedException} is thrown. Any other error (bad key, bad request...) fails at once,
     * because retrying cannot fix it.
     */
    public JsonNode generateContent(List<JsonNode> contents, List<Map<String, Object>> tools, String model) {
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

        JsonNode response = null;
        int maxAttempts = Math.max(1, props.maxAttempts());
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                response = restClient.post()
                        .uri("/models/{model}:generateContent", model)
                        .header("x-goog-api-key", props.apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(JsonNode.class);
                break; // success
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                String detail = errorDetail(e);
                log.warn("Gemini call failed (model {}, attempt {}/{}): HTTP {} {}",
                        model, attempt, maxAttempts, status, detail);
                if (!isRetryable(status)) {
                    // Bad key, bad request, unknown model...: retrying cannot help.
                    throw new GeminiException(HttpStatus.BAD_GATEWAY,
                            "Gemini returned HTTP " + status + (detail.isEmpty() ? "" : ": " + detail), e);
                }
                if (attempt == maxAttempts) {
                    throw new ModelOverloadedException(model, maxAttempts, status);
                }
                pause(attempt);
            } catch (RestClientException e) {
                // Network problem or timeout.
                log.warn("Could not reach Gemini", e);
                throw new GeminiException(HttpStatus.BAD_GATEWAY,
                        "Could not reach Gemini: " + e.getMessage(), e);
            }
        }

        JsonNode content = response == null ? null : response.path("candidates").path(0).path("content");
        if (content == null || content.isMissingNode() || content.isNull()) {
            throw new GeminiException(HttpStatus.BAD_GATEWAY, "Gemini returned an empty answer");
        }
        return content;
    }

    /** Overload and temporary server problems are worth retrying; client mistakes are not. */
    private static boolean isRetryable(int status) {
        return status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
    }

    /** Waits before the next attempt: delay, then 2x delay, then 4x... (exponential backoff). */
    private void pause(int failedAttempt) {
        long millis = props.retryDelayMillis() << (failedAttempt - 1);
        if (millis <= 0) return;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new GeminiException(HttpStatus.SERVICE_UNAVAILABLE, "Interrupted while waiting to retry", ie);
        }
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
