package com.example.shoppingagent.websearch;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings read from application.properties (prefix "serpapi"). */
@ConfigurationProperties(prefix = "serpapi")
public record SerpApiProperties(String apiKey, String baseUrl, int maxResults) {}
