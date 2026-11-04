package com.example.shoppingagent.websearch;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class SerpApiConfig {

    /** HTTP client for SerpApi. Real web searches are slow, so the read timeout is generous. */
    @Bean
    RestClient serpApiRestClient(RestClient.Builder builder, SerpApiProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(20_000);
        return builder.baseUrl(props.baseUrl()).requestFactory(factory).build();
    }
}
