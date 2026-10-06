package com.financetracker.parsing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One transaction as extracted by the LLM from a single raw text chunk -
 * an intermediate, in-memory shape between "LLM's JSON response" and
 * "persisted {@link com.financetracker.parsing.entity.Transaction}
 * entity". A single chunk of statement text typically yields multiple
 * {@code ParsedTransaction}s (one per line-item row in that chunk).
 *
 * @param transactionDate the transaction date as stated on the statement
 * @param description     LLM-cleaned human-readable description
 * @param amount          always a positive magnitude - see {@code direction}
 * @param direction       "CREDIT" or "DEBIT" (money in vs. out)
 */
public record ParsedTransaction(
        LocalDate transactionDate,
        String description,
        BigDecimal amount,
        String direction
) {
}
