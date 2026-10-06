package com.financetracker.categorization.dto;

import jakarta.validation.constraints.AssertTrue;

/**
 * Body for {@code POST /api/v1/transaction-categories/{transactionId}/assign}.
 *
 * <p>Exactly one of the two fields must be set:
 * <ul>
 *   <li>{@code categoryId} - "actually, use this EXISTING category instead"</li>
 *   <li>{@code newCategoryName} - "yes, create this as a genuinely new category"</li>
 * </ul>
 * This mirrors exactly the choice you described: confirm the LLM's
 * suggested new category, or redirect it to one that already exists.
 */
public record AssignCategoryRequest(Long categoryId, String newCategoryName) {

    @AssertTrue(message = "Exactly one of categoryId or newCategoryName must be provided")
    public boolean isExactlyOneFieldSet() {
        return (categoryId != null) ^ (newCategoryName != null && !newCategoryName.isBlank());
    }
}
