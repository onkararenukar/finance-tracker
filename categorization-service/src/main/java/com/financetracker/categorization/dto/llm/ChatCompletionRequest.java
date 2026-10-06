package com.financetracker.categorization.dto.llm;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Same OpenAI-compatible chat completions shape used by parsing-service's
 * {@code ChatCompletionRequest} - duplicated rather than shared for the
 * same cross-service-coupling reasons documented on the event DTOs.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatCompletionRequest(
        String model,
        List<Message> messages,
        Double temperature,
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
