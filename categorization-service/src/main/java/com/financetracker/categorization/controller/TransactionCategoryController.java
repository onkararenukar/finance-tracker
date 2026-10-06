package com.financetracker.categorization.controller;

import com.financetracker.categorization.dto.AssignCategoryRequest;
import com.financetracker.categorization.dto.PendingCategorizationResponse;
import com.financetracker.categorization.entity.CategorizationStatus;
import com.financetracker.categorization.repository.TransactionCategoryRepository;
import com.financetracker.categorization.service.CategorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * The API a future dashboard page calls to render your pending-review
 * queue - every item here is a transaction where the LLM proposed a
 * category name that doesn't exist yet, meant to be shown highlighted in
 * {@code highlightColor} (pinkish-red) until you resolve it via
 * {@link #assignCategory}.
 */
@RestController
@RequestMapping("/api/v1/transaction-categories")
@RequiredArgsConstructor
@Tag(name = "Transaction Categories", description = "Pending category review queue and assignment")
public class TransactionCategoryController {

    private final TransactionCategoryRepository transactionCategoryRepository;
    private final CategorizationService categorizationService;

    @Value("${finance-tracker.ui.pending-review-highlight-color}")
    private String pendingReviewHighlightColor;

    @GetMapping("/pending")
    @Operation(summary = "List transactions awaiting category review",
            description = "Every item here had an LLM-suggested category that didn't match any " +
                    "existing category. Render with highlightColor (pinkish-red) until resolved.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pending categorizations retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PendingCategorizationResponse.class)))
    })
    public Page<PendingCategorizationResponse> listPending(
            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size) {
        return transactionCategoryRepository
                .findByStatus(CategorizationStatus.PENDING_REVIEW, PageRequest.of(page, size))
                .map(tc -> PendingCategorizationResponse.from(tc, pendingReviewHighlightColor));
    }

    @PostMapping("/{transactionId}/assign")
    @Operation(summary = "Resolve a pending categorization",
            description = "Either assign to an existing categoryId, or confirm the suggestion " +
                    "as a genuinely new category via newCategoryName.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Category assigned successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Transaction not found")
    })
    public ResponseEntity<Void> assignCategory(
            @Parameter(description = "Transaction ID")
            @PathVariable Long transactionId,
            @Parameter(description = "Category assignment details")
            @Valid @RequestBody AssignCategoryRequest request) {
        categorizationService.assignCategory(transactionId, request);
        return ResponseEntity.noContent().build();
    }
}
