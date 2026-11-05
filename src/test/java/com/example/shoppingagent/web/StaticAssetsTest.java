package com.example.shoppingagent.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** The demo page and the widget script must be served by the app itself. */
@SpringBootTest
@AutoConfigureMockMvc
class StaticAssetsTest {
    @Autowired MockMvc mvc;

    @Test
    void demoPageIncludesTheWidgetScript() throws Exception {
        mvc.perform(get("/index.html")) // "/" forwards here; MockMvc does not follow forwards
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/widget/shopping-assistant.js")));
    }

    @Test
    void widgetScriptIsServed() throws Exception {
        mvc.perform(get("/widget/shopping-assistant.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("shopping-assistant")));
    }
}
