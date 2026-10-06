package com.financetracker.ingestion.dto;

import com.financetracker.ingestion.entity.BankStatement;

import java.time.Instant;

/**
 * API response returned after a bank statement upload completes.
 *
 * <p>A Java {@code record} rather than a class: DTOs are pure, immutable
 * data carriers, and records give us the constructor, equals/hashCode,
 * toString, and accessors for free with no Lombok needed - and their
 * immutability means a controller can never accidentally mutate a
 * response object after it's been built.
 *
 * <p>Note this deliberately exposes a DTO, not the {@link BankStatement}
 * entity directly. Returning entities from a controller couples your
 * public API contract to your internal DB schema - renaming a column
 * would then be a breaking API change. Mapping through a DTO decouples
 * the two.
 */
public record StatementUploadResponse(
        Long id,
        String bankName,
        String originalFilename,
        String status,
        Integer chunkCount,
        Instant createdAt
) {
    /** Maps the internal entity to the public API shape. */
    public static StatementUploadResponse from(BankStatement statement) {
        return new StatementUploadResponse(
                statement.getId(),
                statement.getBankName(),
                statement.getOriginalFilename(),
                statement.getStatus().name(),
                statement.getChunkCount(),
                statement.getCreatedAt()
        );
    }
}
