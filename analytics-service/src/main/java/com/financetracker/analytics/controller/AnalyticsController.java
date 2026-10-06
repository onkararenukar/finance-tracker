package com.financetracker.analytics.controller;

import com.financetracker.analytics.dto.CategoryBreakdownResponse;
import com.financetracker.analytics.dto.SummaryResponse;
import com.financetracker.analytics.dto.TimeseriesPointResponse;
import com.financetracker.analytics.dto.TransactionListItemResponse;
import com.financetracker.analytics.repository.AnalyticsRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Read-only analytics endpoints for the dashboard. Every endpoint
 * accepts optional {@code from}/{@code to} query params (defaulting to
 * the last 30 days) so the same endpoints serve "this month", "this
 * week", or a custom range with no extra API surface.
 */
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Read-only income/expense aggregations for the dashboard")
public class AnalyticsController {

    /**
     * Postgres's {@code date_trunc} field argument must be a literal,
     * not a bind parameter - see {@code AnalyticsRepository.getTimeseries}.
     * This allowlist is what makes accepting that value from a query
     * parameter safe: anything not EXACTLY one of these three strings is
     * rejected before it ever reaches SQL, so there is no injectable
     * surface despite the string formatting happening downstream.
     */
    private static final Set<String> ALLOWED_GRANULARITIES = Set.of("day", "week", "month");

    private final AnalyticsRepository analyticsRepository;

    @GetMapping("/summary")
    @Operation(summary = "Total income, expense, and net for a date range")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Summary data retrieved successfully",
                    content = @Content(schema = @Schema(implementation = SummaryResponse.class)))
    })
    public SummaryResponse getSummary(
            @Parameter(description = "Start date (ISO format, defaults to 30 days ago)")
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "End date (ISO format, defaults to today)")
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var range = resolveRange(from, to);
        return analyticsRepository.getSummary(range[0], range[1]);
    }

    @GetMapping("/by-category")
    @Operation(summary = "Income/expense totals grouped by category, for the breakdown chart")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Category breakdown retrieved successfully",
                    content = @Content(schema = @Schema(implementation = CategoryBreakdownResponse.class)))
    })
    public List<CategoryBreakdownResponse> getCategoryBreakdown(
            @Parameter(description = "Start date (ISO format, defaults to 30 days ago)")
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "End date (ISO format, defaults to today)")
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var range = resolveRange(from, to);
        return analyticsRepository.getCategoryBreakdown(range[0], range[1]);
    }

    @GetMapping("/timeseries")
    @Operation(summary = "Income/expense totals bucketed by day/week/month, for the trend chart")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Timeseries data retrieved successfully",
                    content = @Content(schema = @Schema(implementation = TimeseriesPointResponse.class)))
    })
    public List<TimeseriesPointResponse> getTimeseries(
            @Parameter(description = "Start date (ISO format, defaults to 30 days ago)")
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "End date (ISO format, defaults to today)")
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Time granularity: day, week, or month (default: day)")
            @RequestParam(value = "granularity", defaultValue = "day") String granularity) {
        if (!ALLOWED_GRANULARITIES.contains(granularity)) {
            throw new IllegalArgumentException(
                    "granularity must be one of " + ALLOWED_GRANULARITIES + ", got: " + granularity);
        }
        var range = resolveRange(from, to);
        return analyticsRepository.getTimeseries(range[0], range[1], granularity);
    }

    @GetMapping("/transactions")
    @Operation(summary = "Paginated transaction list with category and pending-review status")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transaction list retrieved successfully",
                    content = @Content(schema = @Schema(implementation = TransactionListItemResponse.class)))
    })
    public List<TransactionListItemResponse> getTransactions(
            @Parameter(description = "Start date (ISO format, defaults to 30 days ago)")
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "End date (ISO format, defaults to today)")
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Filter by category ID")
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @Parameter(description = "Maximum number of results (1-200, default: 50)")
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @Parameter(description = "Number of results to skip (default: 0)")
            @RequestParam(value = "offset", defaultValue = "0") int offset) {
        var range = resolveRange(from, to);
        int safeLimit = Math.min(Math.max(limit, 1), 200); // hard ceiling - never let a client request an unbounded page
        return analyticsRepository.getTransactions(range[0], range[1], categoryId, safeLimit, offset);
    }

    /** Defaults an unset date range to the last 30 days, inclusive of today. */
    private LocalDate[] resolveRange(LocalDate from, LocalDate to) {
        LocalDate resolvedTo = to != null ? to : LocalDate.now();
        LocalDate resolvedFrom = from != null ? from : resolvedTo.minusDays(30);
        return new LocalDate[]{resolvedFrom, resolvedTo};
    }
}
