package com.financetracker.parsing.dto.event;

import java.time.Instant;
import java.util.List;

/**
 * Consumer-side mirror of ingestion-service's
 * {@code StatementIngestedEvent} - see {@link ExtractedChunk}'s javadoc
 * for why this is duplicated rather than shared.
 */
public record StatementIngestedEvent(
        Long statementId,
        String bankName,
        String fileType,
        List<ExtractedChunk> chunks,
        Instant ingestedAt,
        Long userId
) {
    public StatementIngestedEvent {
        if (userId == null) {
            userId = 1L; // Default to admin user
        }
    }
}
