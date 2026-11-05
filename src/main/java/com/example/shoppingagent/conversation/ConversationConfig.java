package com.example.shoppingagent.conversation;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConversationConfig {

    /** A clock as a bean, so tests can replace it and move time forward without waiting. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
