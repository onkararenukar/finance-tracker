package com.financetracker.ingestion.dto;

/**
 * One chunk of raw, unstructured text pulled out of a bank statement
 * (e.g. a page of a PDF, or a small batch of CSV rows).
 *
 * <p>This mirrors the "chunk -> per row" step from the pipeline design:
 * rather than handing the parsing-service one giant blob of text per
 * statement, we split it into bounded chunks up front. Smaller chunks
 * keep each downstream LLM call's prompt small (cheaper, faster, less
 * likely to hit context limits) and let the parsing-service process
 * chunks of a single statement in parallel.
 *
 * @param chunkIndex  zero-based position of this chunk within the statement,
 *                    so downstream consumers can reassemble/order results
 * @param rawText     the extracted, unstructured text for this chunk
 */
public record ExtractedChunk(int chunkIndex, String rawText) {
}
