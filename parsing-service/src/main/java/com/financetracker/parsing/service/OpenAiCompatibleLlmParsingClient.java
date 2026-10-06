package com.financetracker.parsing.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.financetracker.parsing.dto.ParsedTransaction;
import com.financetracker.parsing.dto.llm.ChatCompletionRequest;
import com.financetracker.parsing.dto.llm.ChatCompletionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * {@link LlmParsingClient} implementation that talks to any
 * OpenAI-compatible {@code /v1/chat/completions} endpoint - whether
 * that's a local Ollama/LM Studio server or a hosted provider, purely
 * based on the {@code finance-tracker.llm.chat.*} config (see
 * {@link com.financetracker.parsing.config.LlmClientConfig}).
 *
 * <p>The prompting strategy: ask for STRICT JSON matching a documented
 * shape, request JSON response-format mode where the provider supports
 * it (a hint, not a guarantee - hence the defensive parsing below), and
 * fail loudly rather than silently swallowing a malformed response,
 * since a silently-dropped transaction is a wrong bank balance on the
 * dashboard, which is much worse than a visible pipeline failure.
 */
@Component
@Slf4j
public class OpenAiCompatibleLlmParsingClient implements LlmParsingClient {

    private final RestClient chatRestClient;
    private final String model;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleLlmParsingClient(
            @Qualifier("chatRestClient") RestClient chatRestClient,
            @Value("${finance-tracker.llm.chat.model}") String model) {
        this.chatRestClient = chatRestClient;
        this.model = model;
        // JavaTimeModule so LocalDate fields in the LLM's JSON response
        // (e.g. "2026-01-15") deserialize correctly; FAIL_ON_UNKNOWN
        // disabled so we don't break if the LLM adds an extra field
        // (e.g. a "merchant" guess) we don't currently use.
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public List<ParsedTransaction> parseChunk(String bankName, String rawText) {
        var request = new ChatCompletionRequest(
                model,
                List.of(
                        new ChatCompletionRequest.Message("system", systemPrompt()),
                        new ChatCompletionRequest.Message("user", userPrompt(bankName, rawText))
                ),
                0.0,   // deterministic extraction, not creative writing - temperature 0
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

        return parseTransactions(response.firstMessageContent());
    }

    @Override
    public String modelName() {
        return model;
    }

    /**
     * Parses the LLM's raw JSON text response into a list of
     * {@link ParsedTransaction}. Wrapped in the top-level
     * {@code {"transactions": [...]}} shape (rather than a bare JSON
     * array) because many providers' JSON-mode enforcement only
     * guarantees the output is a valid JSON OBJECT, not that it's a
     * bare array - see {@code systemPrompt()} for the exact contract
     * communicated to the model.
     */
    private List<ParsedTransaction> parseTransactions(String llmJson) {
        try {
            var wrapper = objectMapper.readValue(llmJson, TransactionsWrapper.class);
            return wrapper.transactions() == null ? List.of() : wrapper.transactions();
        } catch (Exception e) {
            // Deliberately includes the raw LLM output in the exception
            // message (truncated) - when this fails, seeing exactly what
            // the model returned is the single most useful piece of
            // debugging information available.
            String truncated = llmJson.length() > 500 ? llmJson.substring(0, 500) + "..." : llmJson;
            throw new IllegalStateException(
                    "Failed to parse LLM response as structured transactions. Raw response: " + truncated, e);
        }
    }

    private record TransactionsWrapper(List<ParsedTransaction> transactions) {
    }

    private String systemPrompt() {
        return """
                You are a bank statement parsing assistant. You will be given a raw chunk of
                text extracted from a bank statement. Extract every individual transaction
                line-item you can find and respond with ONLY a JSON object in this exact shape,
                with no markdown formatting, no code fences, and no explanation text:

                {
                  "transactions": [
                    {
                      "transactionDate": "YYYY-MM-DD",
                      "description": "short, human-readable description of the transaction",
                      "amount": 123.45,
                      "direction": "CREDIT or DEBIT - CREDIT means money into the account, DEBIT means money out"
                    }
                  ]
                }

                Rules:
                - "amount" must always be a positive number - never negative.
                - If the chunk contains no transactions (e.g. it is a header, footer, or
                  disclaimer section), return {"transactions": []}.
                - Do not guess or invent a transaction that isn't clearly present in the text.
                - Normalize dates to YYYY-MM-DD even if the source uses a different format.
                """;
    }

    private String userPrompt(String bankName, String rawText) {
        return "Bank: " + bankName + "\n\nStatement text chunk:\n" + rawText;
    }
}
