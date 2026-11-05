package com.example.shoppingagent.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param sessionId optional. Send back the id returned by the previous reply to continue that conversation.
 *                  Must be a UUID (hard to guess, because knowing an id is what grants access to its history).
 */
public record ChatRequest(
        @NotBlank @Size(max = 2000) String message,
        @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                message = "sessionId must be a UUID") String sessionId) {}
