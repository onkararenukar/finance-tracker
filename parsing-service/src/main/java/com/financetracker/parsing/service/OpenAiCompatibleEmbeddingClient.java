package com.financetracker.parsing.service;

import com.financetracker.parsing.dto.llm.EmbeddingModels;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
public class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {

    private final RestClient embeddingRestClient;
    private final String model;

    public OpenAiCompatibleEmbeddingClient(
            @Qualifier("embeddingRestClient") RestClient embeddingRestClient,
            @Value("${finance-tracker.llm.embedding.model}") String model) {
        this.embeddingRestClient = embeddingRestClient;
        this.model = model;
    }

    @Override
    public float[] embed(String text) {
        var request = new EmbeddingModels.EmbeddingRequest(model, text);

        EmbeddingModels.EmbeddingResponse response = embeddingRestClient.post()
                .uri("/v1/embeddings")
                .body(request)
                .retrieve()
                .body(EmbeddingModels.EmbeddingResponse.class);

        if (response == null) {
            throw new IllegalStateException("LLM embedding endpoint returned an empty response");
        }
        return response.firstEmbedding();
    }

    @Override
    public String modelName() {
        return model;
    }
}
