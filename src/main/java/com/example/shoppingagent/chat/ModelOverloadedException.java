package com.example.shoppingagent.chat;

import org.springframework.http.HttpStatus;

/**
 * A model kept answering with a temporary error (429 / 5xx) on every attempt.
 * AgentService reacts by trying the fallback model.
 */
public class ModelOverloadedException extends GeminiException {
    public ModelOverloadedException(String model, int attempts, int lastStatus) {
        super(HttpStatus.SERVICE_UNAVAILABLE,
                "Model " + model + " still failing after " + attempts + " attempts (last HTTP " + lastStatus + ")");
    }
}
