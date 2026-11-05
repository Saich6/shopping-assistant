package com.example.shoppingagent.chat;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final AgentService agent;

    public ChatController(AgentService agent) {
        this.agent = agent;
    }

    /**
     * Example body: {"message": "Do you have any TVs on offer?"}
     * Reply: {"reply": "...", "sessionId": "..."}. Send the sessionId back to ask a follow-up question.
     * "produces" declares UTF-8 so clients (e.g. Windows PowerShell) decode characters like ' correctly.
     */
    @PostMapping(produces = "application/json;charset=UTF-8")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        String sessionId = request.sessionId() != null ? request.sessionId() : UUID.randomUUID().toString();
        return new ChatResponse(agent.chat(sessionId, request.message()), sessionId);
    }
}
