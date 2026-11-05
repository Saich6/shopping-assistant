package com.example.shoppingagent.chat;

/** @param sessionId send this back with the next message to keep the conversation going */
public record ChatResponse(String reply, String sessionId) {}
