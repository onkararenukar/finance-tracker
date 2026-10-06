package com.financetracker.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row in the dashboard's transaction list, joining across
 * parsing-service's transactions and categorization-service's
 * transaction_categories/categories tables in a single query.
 *
 * @param categoryName          "Uncategorized" while pending review
 * @param isPendingReview       drives the pinkish-red highlight on the
 *                              dashboard - true until you resolve it via
 *                              categorization-service's assign endpoint
 * @param suggestedCategoryName the LLM's original suggestion, shown
 *                              alongside the highlight so you know what
 *                              you're confirming/redirecting
 */
public record TransactionListItemResponse(
        Long transactionId,
        LocalDate transactionDate,
        String bankName,
        String description,
        BigDecimal amount,
        String direction,
        String categoryName,
        boolean isPendingReview,
        String suggestedCategoryName
) {
}
