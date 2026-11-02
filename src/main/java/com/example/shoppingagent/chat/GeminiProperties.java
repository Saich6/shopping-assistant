package com.example.shoppingagent.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings read from application.properties (prefix "gemini"). */
@ConfigurationProperties(prefix = "gemini")
public record GeminiProperties(String apiKey, String model, String baseUrl) {}
