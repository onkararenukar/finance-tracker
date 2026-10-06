package com.financetracker.analytics.dto;

import java.math.BigDecimal;

/**
 * Top-level income/expense/net totals for a date range - powers the
 * dashboard's summary cards.
 */
public record SummaryResponse(BigDecimal totalIncome, BigDecimal totalExpense, BigDecimal net) {
}
