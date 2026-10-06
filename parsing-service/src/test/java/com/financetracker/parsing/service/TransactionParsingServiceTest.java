package com.financetracker.parsing.service;

import com.financetracker.parsing.dto.ParsedTransaction;
import com.financetracker.parsing.dto.event.ExtractedChunk;
import com.financetracker.parsing.dto.event.StatementIngestedEvent;
import com.financetracker.parsing.dto.event.TransactionParsedEvent;
import com.financetracker.parsing.entity.Transaction;
import com.financetracker.parsing.repository.TransactionEmbeddingStore;
import com.financetracker.parsing.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test for {@link TransactionParsingService}, using a fake
 * {@link LlmParsingClient} instead of a real HTTP call - fast,
 * deterministic, and exercises the Structured Concurrency fan-out logic
 * without needing a live LLM endpoint.
 */
class TransactionParsingServiceTest {

    private TransactionRepository transactionRepository;
    private TransactionEmbeddingStore embeddingStore;
    private KafkaTemplate<String, TransactionParsedEvent> kafkaTemplate;
    private TransactionParsingService service;

    @BeforeEach
    void setUp() {
        transactionRepository = mock(TransactionRepository.class);
        embeddingStore = mock(TransactionEmbeddingStore.class);
        kafkaTemplate = mock(KafkaTemplate.class);

        // saveAll/save just echo back what was passed in, assigning a
        // fake incrementing id so downstream embedding/publish logic has
        // something non-null to work with.
        when(transactionRepository.saveAll(any())).thenAnswer(inv -> {
            List<Transaction> txns = inv.getArgument(0);
            long id = 1L;
            for (Transaction t : txns) {
                ReflectionTestUtils.setField(t, "id", id++);
            }
            return txns;
        });
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, TransactionParsedEvent>> future =
                CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        service = new TransactionParsingService(
                new FakeLlmParsingClient(),
                new FakeEmbeddingClient(),
                transactionRepository,
                embeddingStore,
                kafkaTemplate
        );
        ReflectionTestUtils.setField(service, "transactionParsedTopic", "transaction.parsed");
    }

    @Test
    void processStatement_multipleChunks_parsesAllConcurrentlyAndPublishesEvents() {
        var event = new StatementIngestedEvent(
                42L,
                "Test Bank",
                "CSV",
                List.of(
                        new ExtractedChunk(0, "chunk-0-text"),
                        new ExtractedChunk(1, "chunk-1-text"),
                        new ExtractedChunk(2, "chunk-2-text")
                ),
                Instant.now(),
                null
        );

        service.processStatement(event);

        // FakeLlmParsingClient returns exactly one transaction per chunk,
        // so 3 chunks should yield exactly 3 persisted + published transactions.
        verify(transactionRepository).saveAll(argThat((List<Transaction> txns) -> txns.size() == 3));
        verify(kafkaTemplate, times(3)).send(eq("transaction.parsed"), eq("42"), any());
        verify(embeddingStore, times(3)).save(any(), any(), eq("fake-embedding-model"));
    }

    @Test
    void processStatement_singleChunk_parsesAndPublishesSingleEvent() {
        var event = new StatementIngestedEvent(
                1L,
                "Single Bank",
                "PDF",
                List.of(new ExtractedChunk(0, "single-chunk-text")),
                Instant.now(),
                null
        );

        service.processStatement(event);

        verify(transactionRepository).saveAll(argThat((List<Transaction> txns) -> txns.size() == 1));
        verify(kafkaTemplate, times(1)).send(eq("transaction.parsed"), eq("1"), any());
        verify(embeddingStore, times(1)).save(any(), any(), eq("fake-embedding-model"));
    }

    @Test
    void processStatement_emptyChunks_noTransactionsProcessed() {
        var event = new StatementIngestedEvent(
                1L,
                "Empty Bank",
                "CSV",
                List.of(),
                Instant.now(),
                null
        );

        service.processStatement(event);

        verify(transactionRepository).saveAll(argThat((List<Transaction> txns) -> txns.isEmpty()));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        verify(embeddingStore, never()).save(any(), any(), anyString());
    }

    @Test
    void processStatement_llmReturnsMultipleTransactions_perChunk_processesAll() {
        // Use a fake LLM client that returns multiple transactions per chunk
        service = new TransactionParsingService(
                new MultiTransactionFakeLlmParsingClient(),
                new FakeEmbeddingClient(),
                transactionRepository,
                embeddingStore,
                kafkaTemplate
        );
        ReflectionTestUtils.setField(service, "transactionParsedTopic", "transaction.parsed");

        var event = new StatementIngestedEvent(
                1L,
                "Multi Bank",
                "CSV",
                List.of(new ExtractedChunk(0, "chunk-text")),
                Instant.now(),
                null
        );

        service.processStatement(event);

        verify(transactionRepository).saveAll(argThat((List<Transaction> txns) -> txns.size() == 2));
        verify(kafkaTemplate, times(2)).send(eq("transaction.parsed"), eq("1"), any());
        verify(embeddingStore, times(2)).save(any(), any(), eq("fake-embedding-model"));
    }

    @Test
    void processStatement_withUserId_propagatesToTransactions() {
        var event = new StatementIngestedEvent(
                42L,
                "Test Bank",
                "CSV",
                List.of(new ExtractedChunk(0, "chunk-text")),
                Instant.now(),
                123L // user ID
        );

        service.processStatement(event);

        verify(transactionRepository).saveAll(argThat((List<Transaction> txns) -> {
            return txns.size() == 1 && txns.get(0).getUserId() == 123L;
        }));
    }

    /** Deterministic fake: returns one transaction per chunk, echoing the chunk text into the description. */
    private static class FakeLlmParsingClient implements LlmParsingClient {
        @Override
        public List<ParsedTransaction> parseChunk(String bankName, String rawText) {
            return List.of(new ParsedTransaction(
                    LocalDate.of(2026, 1, 1),
                    "parsed: " + rawText,
                    BigDecimal.TEN,
                    "DEBIT"
            ));
        }

        @Override
        public String modelName() {
            return "fake-model";
        }
    }

    /** Fake LLM client that returns multiple transactions per chunk. */
    private static class MultiTransactionFakeLlmParsingClient implements LlmParsingClient {
        @Override
        public List<ParsedTransaction> parseChunk(String bankName, String rawText) {
            return List.of(
                    new ParsedTransaction(
                            LocalDate.of(2026, 1, 1),
                            "parsed: " + rawText + " (tx1)",
                            BigDecimal.TEN,
                            "DEBIT"
                    ),
                    new ParsedTransaction(
                            LocalDate.of(2026, 1, 2),
                            "parsed: " + rawText + " (tx2)",
                            new BigDecimal("20.00"),
                            "CREDIT"
                    )
            );
        }

        @Override
        public String modelName() {
            return "fake-multi-model";
        }
    }

    /** Deterministic fake embedding client - avoids a real HTTP call in this unit test. */
    private static class FakeEmbeddingClient implements EmbeddingClient {
        @Override
        public float[] embed(String text) {
            return new float[]{0.1f, 0.2f, 0.3f};
        }

        @Override
        public String modelName() {
            return "fake-embedding-model";
        }
    }
}
