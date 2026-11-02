package com.example.shoppingagent.chat;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final GeminiClient gemini;

    public ChatController(GeminiClient gemini) {
        this.gemini = gemini;
    }

    /** Example body: {"message": "What is a good TV under $1000?"} */
    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return new ChatResponse(gemini.generate(request.message()));
    }
}
