package com.example.shoppingagent.conversation;

import java.util.List;

/**
 * Where conversation history lives. The agent only knows this interface, so the in-memory version can be
 * replaced by PostgreSQL or Redis later without touching the agent.
 */
public interface ConversationStore {

    /** Earlier turns of this session, oldest first. Empty for an unknown or expired session. */
    List<Turn> getHistory(String sessionId);

    /** Records a finished question and answer for this session. */
    void addTurn(String sessionId, Turn turn);
}
