package com.financetracker.parsing.dto.llm;

import java.util.List;

/**
 * Response body from the OpenAI-compatible {@code /v1/chat/completions}
 * endpoint. Only the fields this project reads are modeled - Jackson
 * ignores any extra fields a given provider includes beyond these by
 * default configuration in {@link com.financetracker.parsing.config.LlmClientConfig}.
 */
public record ChatCompletionResponse(List<Choice> choices) {

    public record Choice(Message message) {
    }

    public record Message(String role, String content) {
    }

    /** Convenience accessor for the common case of reading the first (only) choice's text. */
    public String firstMessageContent() {
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("LLM response contained no choices");
        }
        return choices.get(0).message().content();
    }
}
