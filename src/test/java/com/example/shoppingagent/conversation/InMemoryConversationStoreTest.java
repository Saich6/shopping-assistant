package com.example.shoppingagent.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryConversationStoreTest {

    /** A clock we can move forward by hand, so expiry tests do not have to wait. */
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T10:00:00Z");
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration d) { now = now.plus(d); }
    }

    MutableClock clock;
    InMemoryConversationStore store;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        store = new InMemoryConversationStore(new ConversationProperties(3, 30, 2), clock);
    }

    static Turn turn(String n) { return new Turn("question " + n, "answer " + n); }

    @Test
    void returnsTurnsInOrder() {
        store.addTurn("s1", turn("1"));
        store.addTurn("s1", turn("2"));
        assertThat(store.getHistory("s1")).containsExactly(turn("1"), turn("2"));
    }

    @Test
    void unknownSessionHasEmptyHistory() {
        assertThat(store.getHistory("nobody")).isEmpty();
    }

    @Test
    void sessionsAreIsolated() {
        store.addTurn("a", turn("A"));
        store.addTurn("b", turn("B"));
        assertThat(store.getHistory("a")).containsExactly(turn("A"));
        assertThat(store.getHistory("b")).containsExactly(turn("B"));
    }

    @Test
    void keepsOnlyTheMostRecentTurns() {
        for (String n : List.of("1", "2", "3", "4", "5")) store.addTurn("s1", turn(n));
        assertThat(store.getHistory("s1")).containsExactly(turn("3"), turn("4"), turn("5")); // max-turns = 3
    }

    @Test
    void idleSessionsExpire() {
        store.addTurn("s1", turn("1"));
        clock.advance(Duration.ofMinutes(29));
        assertThat(store.getHistory("s1")).hasSize(1);   // still alive (and this read counts as activity)
        clock.advance(Duration.ofMinutes(31));
        assertThat(store.getHistory("s1")).isEmpty();    // idle for 31 minutes: forgotten
    }

    @Test
    void dropsLeastRecentlyUsedSessionWhenFull() {
        store.addTurn("old", turn("1"));
        clock.advance(Duration.ofMinutes(1));
        store.addTurn("newer", turn("2"));
        clock.advance(Duration.ofMinutes(1));
        store.addTurn("newest", turn("3"));               // max-sessions = 2, so "old" must go

        assertThat(store.getHistory("old")).isEmpty();
        assertThat(store.getHistory("newer")).hasSize(1);
        assertThat(store.getHistory("newest")).hasSize(1);
    }

    @Test
    void returnedHistoryCannotBeUsedToChangeTheStore() {
        store.addTurn("s1", turn("1"));
        List<Turn> copy = store.getHistory("s1");
        assertThatThrownBy(() -> copy.add(turn("hack"))).isInstanceOf(UnsupportedOperationException.class);
        assertThat(store.getHistory("s1")).hasSize(1);
    }
}
