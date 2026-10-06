package com.financetracker.categorization.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/** Same local-vs-hosted RestClient pattern as parsing-service's LlmClientConfig - see that class for full rationale. */
@Configuration
public class LlmClientConfig {

    @Value("${finance-tracker.llm.chat.base-url}")
    private String baseUrl;

    @Value("${finance-tracker.llm.chat.api-key:}")
    private String apiKey;

    @Value("${finance-tracker.llm.chat.timeout-seconds:30}")
    private int timeoutSeconds;

    @Bean
    public RestClient chatRestClient() {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(timeoutSeconds).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(timeoutSeconds).toMillis());

        var builder = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("Content-Type", "application/json");

        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + apiKey);
        }
        return builder.build();
    }
}
