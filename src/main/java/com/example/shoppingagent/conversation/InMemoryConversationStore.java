package com.example.shoppingagent.conversation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Keeps conversations in the server's memory. Everything is lost on restart, and it works for one server
 * instance only; both are fine for now and are the reasons a database version comes later.
 *
 * Memory is bounded three ways: a few turns per session, sessions expire when idle, and the number of
 * sessions is capped. All methods are synchronized: simple and safe, and fast enough at this scale.
 */
@Component
public class InMemoryConversationStore implements ConversationStore {

    private static final class Session {
        final Deque<Turn> turns = new ArrayDeque<>();
        Instant lastActive;
    }

    private final Map<String, Session> sessions = new HashMap<>();
    private final ConversationProperties props;
    private final Clock clock;

    public InMemoryConversationStore(ConversationProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    @Override
    public synchronized List<Turn> getHistory(String sessionId) {
        removeExpired();
        Session session = sessions.get(sessionId);
        if (session == null) return List.of();
        session.lastActive = clock.instant();
        return List.copyOf(session.turns); // a copy: callers cannot change the stored history
    }

    @Override
    public synchronized void addTurn(String sessionId, Turn turn) {
        removeExpired();
        Session session = sessions.get(sessionId);
        if (session == null) {
            if (sessions.size() >= props.maxSessions()) removeLeastRecentlyUsed();
            session = new Session();
            sessions.put(sessionId, session);
        }
        session.turns.addLast(turn);
        while (session.turns.size() > props.maxTurns()) {
            session.turns.removeFirst(); // forget the oldest whole turn
        }
        session.lastActive = clock.instant();
    }

    private void removeExpired() {
        Instant cutoff = clock.instant().minus(Duration.ofMinutes(props.ttlMinutes()));
        sessions.values().removeIf(s -> s.lastActive.isBefore(cutoff));
    }

    private void removeLeastRecentlyUsed() {
        sessions.entrySet().stream()
                .min(Map.Entry.comparingByValue((a, b) -> a.lastActive.compareTo(b.lastActive)))
                .ifPresent(e -> sessions.remove(e.getKey()));
    }
}
