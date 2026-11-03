package com.example.shoppingagent.chat;

import jakarta.validation.Valid;
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
     * "produces" declares UTF-8 so clients (e.g. Windows PowerShell) decode characters like ' correctly.
     */
    @PostMapping(produces = "application/json;charset=UTF-8")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return new ChatResponse(agent.chat(request.message()));
    }
}
