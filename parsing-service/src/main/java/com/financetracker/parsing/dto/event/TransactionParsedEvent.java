package com.financetracker.parsing.dto.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Published to the {@code transaction.parsed} topic once a transaction
 * has been persisted AND embedded. This is the contract categorization-
 * service consumes: it has everything needed to assign a category and
 * one-line description without querying this service's database directly
 * (each service should own reads/writes of its own tables - see the
 * `statement_id` comment in {@code V1__create_transactions_table.sql}
 * for the same "no cross-service FK" principle applied here).
 *
 * <p>Keyed in Kafka by {@code statementId} (not {@code transactionId}) -
 * see {@code TransactionParsingService.publishParsedEvents} for why:
 * it keeps every transaction from the same statement in partition order.
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
