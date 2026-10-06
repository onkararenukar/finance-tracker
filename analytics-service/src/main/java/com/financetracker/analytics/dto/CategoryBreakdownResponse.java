package com.financetracker.analytics.dto;

import java.math.BigDecimal;

/**
 * One category's total for a date range - powers the dashboard's
 * category breakdown chart (e.g. a pie/bar chart of spending by category).
 *
 * @param categoryName "Uncategorized" for transactions still
 *                     PENDING_REVIEW (see {@code AnalyticsRepository} -
 *                     these are grouped into a synthetic bucket rather
 *                     than silently excluded, so dashboard totals always
 *                     reconcile with the true grand total)
 */
public record CategoryBreakdownResponse(String categoryName, String direction, BigDecimal total, long transactionCount) {
}
