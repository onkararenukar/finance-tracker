package com.financetracker.ingestion.exception;

import java.time.Instant;

/**
 * Uniform error body returned by every failure path in this service, so
 * frontend/API-gateway callers can rely on one consistent JSON shape
 * regardless of which exception was thrown.
 */
public record ErrorResponse(String message, Instant timestamp) {
    public ErrorResponse(String message) {
        this(message, Instant.now());
    }
}
