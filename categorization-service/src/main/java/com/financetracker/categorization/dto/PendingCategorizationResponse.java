package com.financetracker.categorization.dto;

import com.financetracker.categorization.entity.TransactionCategory;

import java.time.Instant;

/**
 * One item in the pending-review queue. Includes {@code highlightColor}
 * directly on the response - rather than making the frontend hardcode
 * "PENDING_REVIEW = pink" as a separate lookup, the color convention you
 * asked for (pinkish-red for anything needing your decision) travels
 * with the data itself. See {@code finance-tracker.ui.pending-review-highlight-color}
 * in application.yml for where the actual value is configured.
 */
public record PendingCategorizationResponse(
        Long transactionId,
        String suggestedCategoryName,
        String oneLineDescription,
        String highlightColor,
        Instant flaggedAt
) {
    public static PendingCategorizationResponse from(TransactionCategory tc, String highlightColor) {
        return new PendingCategorizationResponse(
                tc.getTransactionId(),
                tc.getSuggestedCategoryName(),
                tc.getOneLineDescription(),
                highlightColor,
                tc.getCreatedAt()
        );
    }
}
