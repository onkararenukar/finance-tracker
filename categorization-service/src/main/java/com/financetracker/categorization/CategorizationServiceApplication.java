package com.financetracker.categorization;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Categorization Service.
 *
 * <p>Consumes {@code transaction.parsed}, asks an LLM to categorize each
 * transaction against your existing category list, auto-confirms
 * matches, and flags anything new for your review via
 * {@code GET /api/v1/transaction-categories/pending} /
 * {@code POST /api/v1/transaction-categories/{id}/assign}.
 */
@SpringBootApplication
public class CategorizationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CategorizationServiceApplication.class, args);
    }
}
