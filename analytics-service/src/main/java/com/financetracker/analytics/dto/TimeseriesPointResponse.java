package com.financetracker.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One time bucket's (day/week/month) income and expense totals - powers the dashboard's trend line/bar chart. */
public record TimeseriesPointResponse(LocalDate bucketStart, BigDecimal income, BigDecimal expense) {
}
