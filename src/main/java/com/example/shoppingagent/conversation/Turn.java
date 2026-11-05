package com.example.shoppingagent.conversation;

/**
 * One question and its final answer. We store whole turns, never half of one,
 * so trimming old history can never leave the model with a dangling tool call.
 */
public record Turn(String userMessage, String assistantReply) {}
