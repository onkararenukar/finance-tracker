package com.financetracker.ingestion.dto.event;

import com.financetracker.ingestion.dto.ExtractedChunk;

import java.time.Instant;
import java.util.List;

/**
 * The event payload published to the {@code statement.ingested} Kafka
 * topic once a bank statement has been fully extracted and is ready for
 * the (future) parsing-service to consume.
 *
 * <p>This is the CONTRACT between ingestion-service (producer) and
 * parsing-service (consumer) - both services depend on this shape, so
 * changing a field here is effectively a cross-service API change.
 * Serialized to JSON by Spring Kafka's {@code JsonSerializer} (configured
 * in application.yml), so any consumer - Java or otherwise - can read it
 * without a shared binary schema.
 *
 * <p>We intentionally send the full list of extracted chunks INSIDE the
 * event rather than just an id the consumer must look up. This keeps the
 * parsing-service simple (it doesn't need its own DB read of ingestion's
 * data just to get started) at the cost of a larger Kafka message - an
 * acceptable trade-off given bank statements are small (a few KB-MB of
 * text, well under Kafka's default 1MB message size in the vast majority
 * of cases; very large statements should be chunked small enough that the
 * whole event still fits).
 *
 * @param statementId   the bank_statements.id this event refers to
 * @param bankName      which bank the statement is from (helps the LLM
 *                      prompt in parsing-service tailor its parsing to
 *                      that bank's known statement format)
 * @param fileType      "PDF" or "CSV"
 * @param chunks        the extracted, ordered text chunks to be parsed
 * @param ingestedAt    when this event was published (for latency metrics
 *                      and for the live Kafka-visuals dashboard page)
 */
public record StatementIngestedEvent(
        Long statementId,
        String bankName,
        String fileType,
        List<ExtractedChunk> chunks,
        Instant ingestedAt
) {
}
