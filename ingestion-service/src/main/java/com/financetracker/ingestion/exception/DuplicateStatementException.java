package com.financetracker.ingestion.exception;

/**
 * Thrown when an uploaded file's checksum matches a statement already
 * on record - i.e. the exact same file has been uploaded before.
 * Mapped to HTTP 409 Conflict by {@link GlobalExceptionHandler}.
 */
public class DuplicateStatementException extends RuntimeException {
    public DuplicateStatementException(String message) {
        super(message);
    }
}
