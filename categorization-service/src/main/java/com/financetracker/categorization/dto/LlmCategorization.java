package com.financetracker.categorization.dto;

/**
 * The LLM's categorization output for one transaction, before we've
 * checked whether {@code categoryName} matches an existing category.
 *
 * @param categoryName       the category the LLM believes this transaction
 *                           belongs to - may or may not match an existing one
 * @param oneLineDescription a short, human-readable summary of the
 *                           transaction (per the original design notes:
 *                           "give it a category and short one line description")
 */
public record LlmCategorization(String categoryName, String oneLineDescription) {
}
