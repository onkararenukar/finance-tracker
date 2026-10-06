package com.financetracker.parsing.dto.event;

/**
 * Mirrors {@code com.financetracker.ingestion.dto.ExtractedChunk} field
 * for field.
 *
 * <p>Why duplicated instead of shared via a common library: in a
 * microservice architecture, sharing DTO classes through a shared JAR
 * creates a deployment coupling - upgrading the shared library becomes a
 * cross-team/cross-service coordination problem, and it's easy to end up
 * with services silently running different versions of "the same"
 * class. Treating the Kafka message schema as the CONTRACT (documented
 * here, on both ends) rather than sharing Java classes keeps each
 * service independently deployable, at the small cost of keeping this
 * record's shape in sync by hand with the producer. For a project this
 * size that trade-off favors simplicity; a larger org would likely
 * introduce a schema registry (e.g. Avro/Protobuf + Confluent Schema
 * Registry) to enforce and evolve this contract safely instead.
 */
public record ExtractedChunk(int chunkIndex, String rawText) {
}
