package com.financetracker.parsing.service;

import com.financetracker.parsing.dto.ParsedTransaction;
import com.financetracker.parsing.dto.event.ExtractedChunk;
import com.financetracker.parsing.dto.event.StatementIngestedEvent;
import com.financetracker.parsing.dto.event.TransactionParsedEvent;
import com.financetracker.parsing.entity.Transaction;
import com.financetracker.parsing.entity.TransactionDirection;
import com.financetracker.parsing.entity.TransactionStatus;
import com.financetracker.parsing.repository.TransactionEmbeddingStore;
import com.financetracker.parsing.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Orchestrates everything that happens once a {@link StatementIngestedEvent}
 * arrives: parse every chunk concurrently, persist the resulting
 * transactions, embed each one, and publish a {@link TransactionParsedEvent}
 * per transaction.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionParsingService {

    private final LlmParsingClient llmParsingClient;
    private final EmbeddingClient embeddingClient;
    private final TransactionRepository transactionRepository;
    private final TransactionEmbeddingStore embeddingStore;
    private final KafkaTemplate<String, TransactionParsedEvent> transactionParsedKafkaTemplate;

    @Value("${finance-tracker.kafka.topics.transaction-parsed}")
    private String transactionParsedTopic;

    /**
     * Processes one ingested statement end to end. Throws on any
     * unrecoverable failure - the caller ({@code StatementIngestedListener})
     * decides what to do with that (dead-letter + ack, so one bad
     * statement never blocks the whole consumer group).
     */
    public void processStatement(StatementIngestedEvent event) {
        log.info("Processing statement id={} with {} chunks", event.statementId(), event.chunks().size());

        List<ParsedTransaction> allParsed = parseChunksConcurrently(event);
        log.info("Statement id={} yielded {} raw parsed transactions", event.statementId(), allParsed.size());

        List<Transaction> saved = persistTransactions(event, allParsed);
        embedAndPublish(saved, event.statementId());

        log.info("Finished processing statement id={} - {} transactions persisted",
                event.statementId(), saved.size());
    }

    /**
     * Fans out one LLM call per chunk CONCURRENTLY using ExecutorService
     * with virtual threads for Java 21 compatibility.
     *
     * <p>Uses virtual threads via Executors.newVirtualThreadPerTaskExecutor()
     * to achieve similar concurrency benefits as StructuredTaskScope,
     * but with Java 21 compatible APIs.
     */
    private List<ParsedTransaction> parseChunksConcurrently(StatementIngestedEvent event) {
        List<ExtractedChunk> chunks = event.chunks();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

            // Submit one task per chunk. Each task runs on its own virtual thread.
            List<Future<List<ParsedTransaction>>> futures = chunks.stream()
                    .map(chunk -> executor.submit(() -> llmParsingClient.parseChunk(event.bankName(), chunk.rawText())))
                    .collect(Collectors.toList());

            List<ParsedTransaction> result = new ArrayList<>();
            for (Future<List<ParsedTransaction>> future : futures) {
                try {
                    result.addAll(future.get());
                } catch (ExecutionException e) {
                    throw new IllegalStateException(
                            "Failed to parse one or more chunks for statement id=" + event.statementId(), e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(
                            "Interrupted while parsing chunks for statement id=" + event.statementId(), e);
                }
            }
            return result;

        }
    }

    /**
     * Persists every parsed transaction. Note this deliberately loses
     * the association between a {@link ParsedTransaction} and its exact
     * source {@link ExtractedChunk} once chunks are flattened in
     * {@link #parseChunksConcurrently} - a known simplification for v1.
     * A future improvement would carry the chunk index through
     * end-to-end (e.g. by having each subtask return a small
     * (chunkIndex, transactions) pair) so {@code source_chunk_index} and
     * {@code source_text} are always exactly correct rather than best-effort.
     */
    private List<Transaction> persistTransactions(StatementIngestedEvent event, List<ParsedTransaction> parsed) {
        // Use user_id from the event for multi-tenant support
        final Long effectiveUserId = event.userId();
        
        List<Transaction> entities = parsed.stream()
                .map(p -> new Transaction(
                        event.statementId(),
                        0, // see javadoc above - chunk index association simplified for v1
                        event.bankName(),
                        p.transactionDate(),
                        p.description(),
                        p.amount(),
                        TransactionDirection.valueOf(p.direction()),
                        p.description(), // source_text: acceptable v1 stand-in until chunk tracking above is added
                        llmParsingClient.modelName(),
                        effectiveUserId
                ))
                .toList();

        return transactionRepository.saveAll(entities);
    }

    /**
     * Computes and stores an embedding for each transaction, then
     * publishes its {@link TransactionParsedEvent}. Runs sequentially
     * (unlike the chunk-parsing fan-out above) - kept simple for v1 since
     * embedding calls are typically much cheaper/faster than chat
     * completions; parallelizing this too with another
     * {@code StructuredTaskScope} is a straightforward follow-up if
     * profiling shows it's worth it.
     */
    private void embedAndPublish(List<Transaction> transactions, Long statementId) {
        for (Transaction transaction : transactions) {
            try {
                float[] embedding = embeddingClient.embed(transaction.getDescription());
                embeddingStore.save(transaction.getId(), embedding, embeddingClient.modelName());
                transaction.setStatus(TransactionStatus.EMBEDDED);
                transactionRepository.save(transaction);

                publishParsedEvent(transaction, statementId);

                transaction.setStatus(TransactionStatus.PUBLISHED);
                transactionRepository.save(transaction);

            } catch (RuntimeException ex) {
                // One transaction's embedding failing shouldn't abort the
                // whole statement - log it, mark it for a retry job (see
                // TransactionRepository.findByStatus), and keep going.
                log.error("Embedding failed for transaction id={}", transaction.getId(), ex);
                transaction.setStatus(TransactionStatus.EMBEDDING_FAILED);
                transactionRepository.save(transaction);
            }
        }
    }

    /**
     * Publishes keyed by {@code statementId}, not {@code transactionId} -
     * this guarantees every transaction from the same statement is
     * delivered to categorization-service consumers in the same relative
     * order they were persisted in, which matters if categorization ever
     * needs statement-level context (e.g. "these transactions are from
     * the same statement, categorize them consistently").
     */
    private void publishParsedEvent(Transaction transaction, Long statementId) {
        var event = new TransactionParsedEvent(
                transaction.getId(),
                statementId,
                transaction.getBankName(),
                transaction.getTransactionDate(),
                transaction.getDescription(),
                transaction.getAmount(),
                transaction.getDirection().name(),
                Instant.now()
        );

        try {
            transactionParsedKafkaTemplate.send(transactionParsedTopic, String.valueOf(statementId), event).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing transaction.parsed event", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to publish transaction.parsed event for transaction id="
                    + transaction.getId(), e.getCause());
        }
    }
}
