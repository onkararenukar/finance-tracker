package com.financetracker.categorization.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.financetracker.categorization.dto.LlmCategorization;
import com.financetracker.categorization.dto.llm.ChatCompletionRequest;
import com.financetracker.categorization.dto.llm.ChatCompletionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

@Component
@Slf4j
public class OpenAiCompatibleLlmCategorizationClient implements LlmCategorizationClient {

    private final RestClient chatRestClient;
    private final String model;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public OpenAiCompatibleLlmCategorizationClient(
            RestClient chatRestClient,
            @Value("${finance-tracker.llm.chat.model}") String model) {
        this.chatRestClient = chatRestClient;
        this.model = model;
    }

    @Override
    public LlmCategorization categorize(String bankName, String description, BigDecimal amount,
                                         String direction, List<String> existingCategoryNames) {
        var request = new ChatCompletionRequest(
                model,
                List.of(
                        new ChatCompletionRequest.Message("system", systemPrompt(existingCategoryNames)),
                        new ChatCompletionRequest.Message("user",
                                userPrompt(bankName, description, amount, direction))
                ),
                0.0,
                ChatCompletionRequest.ResponseFormat.json()
        );

        ChatCompletionResponse response = chatRestClient.post()
                .uri("/v1/chat/completions")
                .body(request)
                .retrieve()
                .body(ChatCompletionResponse.class);

        if (response == null) {
            throw new IllegalStateException("LLM chat endpoint returned an empty response");
        }

        return parse(response.firstMessageContent());
    }

    @Override
    public String modelName() {
        return model;
    }

    private LlmCategorization parse(String llmJson) {
        try {
            return objectMapper.readValue(llmJson, LlmCategorization.class);
        } catch (Exception e) {
            String truncated = llmJson.length() > 300 ? llmJson.substring(0, 300) + "..." : llmJson;
            throw new IllegalStateException(
                    "Failed to parse LLM categorization response. Raw response: " + truncated, e);
        }
    }

    private String systemPrompt(List<String> existingCategoryNames) {
        return """
                You are a personal finance categorization assistant. Given a single bank \
                transaction, respond with ONLY a JSON object in this exact shape, no markdown, \
                no code fences, no explanation:

                {
                  "categoryName": "the best-fitting category name",
                  "oneLineDescription": "a short, human-readable summary of what this transaction was for"
                }

                Here is the list of categories that ALREADY EXIST in the system:
                %s

                Rules:
                - STRONGLY prefer reusing one of the existing categories above, matching by
                  meaning (e.g. a transaction described as "Swiggy order" should map to
                  "Food & Dining" if that exists, even though the exact words differ).
                - Only propose a category name NOT in the list above if none of the existing
                  categories reasonably fit this transaction.
                - "categoryName" must be a short, human-readable name (e.g. "Pet Care"), not a
                  sentence.
                """.formatted(String.join(", ", existingCategoryNames));
    }

    private String userPrompt(String bankName, String description, BigDecimal amount, String direction) {
        return "Bank: %s\nDescription: %s\nAmount: %s\nDirection: %s"
                .formatted(bankName, description, amount, direction);
    }
}
