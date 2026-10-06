package com.financetracker.parsing.dto.llm;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Request body for the OpenAI-compatible {@code /v1/chat/completions}
 * endpoint. This shape is supported not just by OpenAI itself but also
 * by local model servers like Ollama and LM Studio, and by most hosted
 * alternatives (Groq, Together, etc.) - which is exactly what makes the
 * "local / api call" toggle in {@code application.yml} a pure
 * configuration change rather than a code change.
 *
 * <p>Only the fields this project actually uses are modeled - not the
 * full API surface. {@code @JsonInclude(NON_NULL)} keeps optional fields
 * (like {@code temperature}) out of the JSON body entirely when unset,
 * rather than sending an explicit {@code null} some servers might reject.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatCompletionRequest(
        String model,
        List<Message> messages,
        Double temperature,
        /** Requests strict JSON output where the provider supports it (OpenAI, many local servers via Ollama's `format: json`). */
        ResponseFormat response_format
) {
    public record Message(String role, String content) {
    }

    public record ResponseFormat(String type) {
        public static ResponseFormat json() {
            return new ResponseFormat("json_object");
        }
    }
}
