package com.financetracker.categorization.dto.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Mirrors parsing-service's {@code TransactionParsedEvent} - see
 * ingestion/parsing's {@code ExtractedChunk} javadoc for why event
 * contracts are duplicated per-service rather than shared via a common JAR.
 */
public record TransactionParsedEvent(
        Long transactionId,
        Long statementId,
        String bankName,
        LocalDate transactionDate,
        String description,
        BigDecimal amount,
        String direction,
        Instant parsedAt
) {
}
