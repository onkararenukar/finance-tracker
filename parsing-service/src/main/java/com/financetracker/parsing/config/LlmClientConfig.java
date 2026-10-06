package com.financetracker.parsing.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Wires the two HTTP clients used to talk to an LLM: one for chat
 * completions (statement -> structured transactions) and one for
 * embeddings (transaction description -> vector). Kept as two separate
 * {@link RestClient} beans, each with its own base URL/timeout, because
 * in practice chat and embedding models are very often served from
 * different endpoints (e.g. a hosted chat API + a local embedding model
 * for cost reasons, or vice versa) - see the two independent
 * {@code finance-tracker.llm.chat.*} / {@code finance-tracker.llm.embedding.*}
 * blocks in application.yml.
 *
 * <p>Using Spring's {@link RestClient} (the modern, synchronous
 * successor to {@code RestTemplate}) rather than {@code WebClient} -
 * this service's request handling already runs on virtual threads
 * (application.yml: {@code spring.threads.virtual.enabled}), so a
 * blocking HTTP call here parks the virtual thread cheaply instead of
 * tying up a scarce platform thread. That removes the main reason to
 * reach for WebFlux's reactive, non-blocking client in the first place.
 */
@Configuration
public class LlmClientConfig {

    @Value("${finance-tracker.llm.chat.base-url}")
    private String chatBaseUrl;

    @Value("${finance-tracker.llm.chat.api-key:}")
    private String chatApiKey;

    @Value("${finance-tracker.llm.chat.timeout-seconds:60}")
    private int chatTimeoutSeconds;

    @Value("${finance-tracker.llm.embedding.base-url}")
    private String embeddingBaseUrl;

    @Value("${finance-tracker.llm.embedding.api-key:}")
    private String embeddingApiKey;

    @Value("${finance-tracker.llm.embedding.timeout-seconds:30}")
    private int embeddingTimeoutSeconds;

    @Bean
    public RestClient chatRestClient() {
        return buildClient(chatBaseUrl, chatApiKey, chatTimeoutSeconds);
    }

    @Bean
    public RestClient embeddingRestClient() {
        return buildClient(embeddingBaseUrl, embeddingApiKey, embeddingTimeoutSeconds);
    }

    private RestClient buildClient(String baseUrl, String apiKey, int timeoutSeconds) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) java.time.Duration.ofSeconds(timeoutSeconds).toMillis());
        requestFactory.setReadTimeout((int) java.time.Duration.ofSeconds(timeoutSeconds).toMillis());

        var builder = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("Content-Type", "application/json");

        // Local model servers (Ollama, LM Studio) typically need no auth
        // at all, so the api-key property is allowed to be blank - only
        // attach the Authorization header when a key is actually configured.
        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + apiKey);
        }

        return builder.build();
    }
}
