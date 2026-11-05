package com.example.shoppingagent.conversation;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings read from application.properties (prefix "conversation").
 *
 * @param maxTurns    how many recent question/answer pairs are remembered per session
 * @param ttlMinutes  a session with no activity for this long is forgotten
 * @param maxSessions upper limit on sessions held at once; the least recently used is dropped beyond it
 */
@ConfigurationProperties(prefix = "conversation")
public record ConversationProperties(int maxTurns, int ttlMinutes, int maxSessions) {}
