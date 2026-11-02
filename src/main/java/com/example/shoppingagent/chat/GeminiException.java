package com.example.shoppingagent.chat;

import org.springframework.http.HttpStatus;

/** Something went wrong talking to Gemini. Carries the HTTP status we should return to our caller. */
public class GeminiException extends RuntimeException {
    private final HttpStatus status;

    public GeminiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public GeminiException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }
}
