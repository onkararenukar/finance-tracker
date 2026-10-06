package com.financetracker.categorization.service;

import com.financetracker.categorization.dto.LlmCategorization;

import java.math.BigDecimal;
import java.util.List;

/** See parsing-service's {@code LlmParsingClient} for why this is an interface (testability + swappable providers). */
public interface LlmCategorizationClient {

    /**
     * Asks the LLM to categorize one transaction, GIVEN the list of
     * categories that already exist - the prompt explicitly asks it to
     * reuse one of these where a reasonable match exists, and only
     * propose a new name when none of them fit.
     *
     * @param existingCategoryNames the current category list, so the LLM
     *                              is nudged toward reuse over invention
     */
    LlmCategorization categorize(String bankName, String description, BigDecimal amount, String direction,
                                  List<String> existingCategoryNames);

    String modelName();
}
