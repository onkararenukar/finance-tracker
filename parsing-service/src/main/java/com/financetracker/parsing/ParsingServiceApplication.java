package com.financetracker.parsing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Parsing Service.
 *
 * <p>Consumes {@code statement.ingested} Kafka events, calls an LLM to
 * turn each raw text chunk into structured transactions (using Java 24's
 * preview Structured Concurrency to parse all of a statement's chunks in
 * parallel - see {@link com.financetracker.parsing.service.TransactionParsingService}),
 * persists them to Postgres, computes and stores a pgvector embedding
 * per transaction, and publishes a {@code transaction.parsed} event per
 * transaction for categorization-service to consume next.
 */
@SpringBootApplication
public class ParsingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ParsingServiceApplication.class, args);
    }
}
