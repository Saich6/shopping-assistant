package com.example.shoppingagent.chat;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ChatController.class)
class ChatControllerTest {
    @Autowired MockMvc mvc;
    @MockBean AgentService agent;

    @Test
    void returnsReply() throws Exception {
        when(agent.chat(anyString(), eq("hello"))).thenReturn("Hi!");
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("charset=UTF-8")))
                .andExpect(jsonPath("$.reply").value("Hi!"))
                .andExpect(jsonPath("$.sessionId").isNotEmpty());   // a new conversation gets an id
    }

    @Test
    void usesAndEchoesTheSessionIdFromTheClient() throws Exception {
        String id = "11111111-1111-1111-1111-111111111111";
        when(agent.chat(id, "again")).thenReturn("Sure.");
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"again\",\"sessionId\":\"" + id + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(id));
        verify(agent).chat(id, "again");
    }

    @Test
    void sessionIdThatIsNotAUuidIsRejected() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\",\"sessionId\":\"admin\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankMessageIsBadRequest() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void geminiFailureBecomesJsonError() throws Exception {
        when(agent.chat(anyString(), eq("hello"))).thenThrow(new GeminiException(HttpStatus.BAD_GATEWAY, "boom"));
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("boom"));
    }
}
