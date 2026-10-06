package com.financetracker.ingestion.exception;

/**
 * Thrown when an uploaded file is neither a PDF nor a CSV, or when text
 * extraction from an otherwise-supported file type fails (e.g. a
 * password-protected or corrupted PDF). Mapped to HTTP 422 Unprocessable
 * Entity by {@link GlobalExceptionHandler}.
 */
public class UnsupportedStatementFormatException extends RuntimeException {
    public UnsupportedStatementFormatException(String message) {
        super(message);
    }

    public UnsupportedStatementFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
