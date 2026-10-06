package com.financetracker.categorization.dto.llm;

import java.util.List;

public record ChatCompletionResponse(List<Choice> choices) {

    public record Choice(Message message) {
    }

    public record Message(String role, String content) {
    }

    public String firstMessageContent() {
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("LLM response contained no choices");
        }
        return choices.get(0).message().content();
    }
}
