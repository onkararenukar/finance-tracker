package com.financetracker.ingestion.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralized exception -> HTTP response mapping for the whole service.
 *
 * <p>Without this, an uncaught exception in a controller/service method
 * would produce Spring Boot's generic "Whitelabel Error Page" (or a raw
 * stack trace as JSON) - neither of which is useful to an API consumer
 * (the future frontend, or the API gateway). Every handler here returns
 * the same {@link ErrorResponse} shape for consistency.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateStatementException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateStatementException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(UnsupportedStatementFormatException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedFormat(UnsupportedStatementFormatException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse(ex.getMessage()));
    }

    /** Triggered by @Valid failures on request DTOs (e.g. missing required field). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        var message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        return ResponseEntity.badRequest().body(new ErrorResponse(message));
    }

    /** Last-resort catch-all so no exception ever leaks an internal stack trace to a client. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse("Unexpected error: " + ex.getMessage()));
    }
}
