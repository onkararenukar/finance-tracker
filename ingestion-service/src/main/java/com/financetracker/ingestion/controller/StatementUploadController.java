package com.financetracker.ingestion.controller;

import com.financetracker.ingestion.dto.StatementUploadResponse;
import com.financetracker.ingestion.service.StatementIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Public HTTP entrypoint for the ingestion service.
 *
 * <p>Sits behind the API gateway in the full architecture - this
 * controller doesn't do its own auth/rate-limiting, trusting the
 * gateway to have handled that already. Kept intentionally thin: all
 * real logic lives in {@link StatementIngestionService}, so this class
 * is easy to read as "here is the shape of the API" without wading
 * through business logic.
 */
@RestController
@RequestMapping("/api/v1/statements")
@RequiredArgsConstructor
@Validated
@Tag(name = "Statements", description = "Bank statement upload and ingestion")
public class StatementUploadController {

    private final StatementIngestionService ingestionService;

    /**
     * Uploads a single bank statement (PDF or CSV) for a given bank.
     *
     * <p>Synchronous from the caller's point of view - the HTTP response
     * only comes back once the file has been extracted and the Kafka
     * event published (or failed). This is a deliberate simplicity
     * trade-off for v1: a fully async "202 Accepted + poll for status"
     * flow is a natural upgrade later if extraction time ever becomes
     * a UX problem, without changing this method's public contract
     * (the response body already includes a status field a client
     * could poll on if we later added a GET /statements/{id} endpoint).
     */
    @PostMapping(consumes = "multipart/form-data")
    @Operation(
            summary = "Upload a bank statement",
            description = "Accepts a PDF or CSV bank statement, extracts its raw text, " +
                    "stores a tracking record, and publishes a statement.ingested Kafka event " +
                    "for downstream parsing."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Statement uploaded successfully",
                    content = @Content(schema = @Schema(implementation = StatementUploadResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid file or parameters"),
            @ApiResponse(responseCode = "500", description = "Internal server error during processing")
    })
    public ResponseEntity<StatementUploadResponse> uploadStatement(
            @Parameter(description = "The statement file (PDF or CSV)")
            @RequestParam("file") MultipartFile file,

            @Parameter(description = "Name of the bank this statement is from, e.g. 'HDFC Bank'")
            @RequestParam("bankName") @NotBlank String bankName,

            @RequestHeader(value = "X-User-Id", required = false) Long userId
    ) {
        // Default to admin user (id=1) if no user context provided
        // This maintains backward compatibility during transition
        Long effectiveUserId = (userId != null) ? userId : 1L;
        
        var statement = ingestionService.ingest(file, bankName, effectiveUserId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(StatementUploadResponse.from(statement));
    }
}
