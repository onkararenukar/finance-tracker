package com.financetracker.categorization.listener;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.financetracker.categorization.dto.event.TransactionParsedEvent;
import com.financetracker.categorization.service.CategorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code transaction.parsed} events. Same manual-deserialize /
 * manual-ack / dead-letter-on-failure pattern as parsing-service's
 * {@code StatementIngestedListener} - see that class's javadoc for the
 * full rationale (cross-service classpath mismatch on Kafka's type
 * headers, and "dead-letter and move on" over "retry forever" for
 * poison messages).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TransactionParsedListener {

    private final CategorizationService categorizationService;
    private final KafkaTemplate<String, String> deadLetterKafkaTemplate;

    @Value("${finance-tracker.kafka.topics.transaction-parsed-dlt}")
    private String dltTopic;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @KafkaListener(
            topics = "${finance-tracker.kafka.topics.transaction-parsed}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onTransactionParsed(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String rawPayload = record.value();
        try {
            TransactionParsedEvent event = objectMapper.readValue(rawPayload, TransactionParsedEvent.class);
            categorizationService.categorize(
                    event.transactionId(), event.bankName(), event.description(),
                    event.amount(), event.direction());
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to categorize transaction.parsed message at offset={} partition={} - sending to DLT",
                    record.offset(), record.partition(), ex);
            try {
                deadLetterKafkaTemplate.send(dltTopic, record.key(), rawPayload).get();
            } catch (Exception dltEx) {
                log.error("Failed to publish to dead-letter topic {} for key={}", dltTopic, record.key(), dltEx);
            }
            ack.acknowledge();
        }
    }
}
