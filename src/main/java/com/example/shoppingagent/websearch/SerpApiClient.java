package com.example.shoppingagent.websearch;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Searches Google Shopping through SerpApi (engine=google_shopping). */
@Component
public class SerpApiClient {
    private static final Logger log = LoggerFactory.getLogger(SerpApiClient.class);

    private static final long CACHE_TTL_MILLIS = 10 * 60 * 1000L;
    private static final int CACHE_MAX_ENTRIES = 100;

    private record CacheEntry(long storedAt, List<WebProduct> products) {}

    private final RestClient restClient;
    private final SerpApiProperties props;
    /** Each search costs one SerpApi credit, so identical searches within 10 minutes are served from memory. */
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public SerpApiClient(@Qualifier("serpApiRestClient") RestClient restClient, SerpApiProperties props) {
        this.restClient = restClient;
        this.props = props;
    }

    public List<WebProduct> searchShopping(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query is required");
        }
        if (props.apiKey() == null || props.apiKey().isBlank()) {
            throw new IllegalStateException("SERPAPI_API_KEY is not set");
        }

        String cacheKey = query.trim().toLowerCase(Locale.ROOT);
        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && System.currentTimeMillis() - cached.storedAt() < CACHE_TTL_MILLIS) {
            return cached.products();
        }

        List<WebProduct> products = fetch(query.trim());
        if (cache.size() >= CACHE_MAX_ENTRIES) cache.clear();
        cache.put(cacheKey, new CacheEntry(System.currentTimeMillis(), products));
        return products;
    }

    private List<WebProduct> fetch(String query) {
        JsonNode response;
        try {
            response = restClient.get()
                    .uri(u -> u.path("/search.json")
                            .queryParam("engine", "google_shopping")
                            .queryParam("q", "{q}")
                            .queryParam("api_key", "{key}")
                            .queryParam("gl", "us")
                            .queryParam("hl", "en")
                            .build(Map.of("q", query, "key", props.apiKey())))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            log.warn("SerpApi returned HTTP {}", e.getStatusCode().value());
            throw new IllegalStateException("SerpApi returned HTTP " + e.getStatusCode().value());
        } catch (RestClientException e) {
            // Do NOT include e.getMessage(): it contains the request URL, which contains the API key.
            log.warn("Could not reach SerpApi ({})", e.getClass().getSimpleName());
            throw new IllegalStateException("Could not reach SerpApi");
        }
        return parse(response);
    }

    private List<WebProduct> parse(JsonNode response) {
        JsonNode results = response == null ? null : response.path("shopping_results");
        if (results == null || !results.isArray()) {
            // SerpApi reports "no results" as {"error": "..."}; anything else is a real failure.
            String error = response == null ? "" : response.path("error").asText("");
            if (error.isEmpty() || error.toLowerCase(Locale.ROOT).contains("any results")) {
                return List.of();
            }
            throw new IllegalStateException("SerpApi error: " + error);
        }

        List<WebProduct> products = new ArrayList<>();
        for (JsonNode item : results) {
            if (products.size() >= props.maxResults()) break;
            products.add(toProduct(item));
        }
        return products;
    }

    private static WebProduct toProduct(JsonNode item) {
        List<String> notes = new ArrayList<>();
        item.path("extensions").forEach(n -> notes.add(n.asText()));
        if (item.hasNonNull("delivery")) notes.add(item.get("delivery").asText());
        // NOTE: "old_price" is deliberately ignored. In real results it can be a unit price
        // such as "$0.41/fl oz", not a previous price, so using it would invent discounts.
        return new WebProduct(
                item.path("title").asText(""),
                item.hasNonNull("extracted_price") ? item.get("extracted_price").decimalValue() : null,
                item.path("source").asText(""),
                item.hasNonNull("rating") ? item.get("rating").asDouble() : null,
                item.hasNonNull("reviews") ? item.get("reviews").asInt() : null,
                item.path("product_link").asText(""),
                notes);
    }
}
