package com.example.shoppingagent.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings read from application.properties (prefix "gemini").
 *
 * @param fallbackModel    model to use if the main one stays overloaded (blank = no fallback)
 * @param maxAttempts      how many times each model is tried before giving up on it
 * @param retryDelayMillis wait before the 2nd attempt; doubles on each further attempt (1s, 2s, ...)
 */
@ConfigurationProperties(prefix = "gemini")
public record GeminiProperties(String apiKey, String model, String fallbackModel, String baseUrl,
                               int maxAttempts, long retryDelayMillis) {}
