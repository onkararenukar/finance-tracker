package com.financetracker.parsing.service;

import com.financetracker.parsing.dto.ParsedTransaction;

import java.util.List;

/**
 * Abstraction over "call an LLM to turn raw statement text into
 * structured transactions".
 *
 * <p>Defined as an interface (implemented by
 * {@link OpenAiCompatibleLlmParsingClient}) rather than calling an HTTP
 * client directly from {@code TransactionParsingService}, so that:
 * <ul>
 *   <li>Unit tests can supply a fake/mock implementation with no network
 *       calls and no LLM non-determinism</li>
 *   <li>Swapping providers (local Ollama model vs. a hosted API) later
 *       is a new implementation of this interface, not a rewrite of the
 *       orchestration logic that calls it</li>
 * </ul>
 */
public interface LlmParsingClient {

    /**
     * Sends one raw text chunk to the LLM and returns the transactions
     * it extracted.
     *
     * @param bankName the bank this statement is from, included in the
     *                 prompt so the LLM can lean on known formatting
     *                 conventions for that bank if it recognizes them
     * @param rawText  the raw, unstructured chunk text (a page of PDF
     *                 text or a block of CSV rows) to parse
     * @return zero or more structured transactions found in this chunk
     */
    List<ParsedTransaction> parseChunk(String bankName, String rawText);

    /** Identifies which model produced the extraction, stored on each Transaction row. */
    String modelName();
}
