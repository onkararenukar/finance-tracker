package com.financetracker.parsing.listener;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.financetracker.parsing.dto.event.StatementIngestedEvent;
import com.financetracker.parsing.service.TransactionParsingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code statement.ingested} events published by ingestion-service.
 *
 * <p><b>Why we deserialize manually via {@link ObjectMapper} instead of
 * configuring Spring Kafka's {@code JsonDeserializer} to do it:</b> the
 * producer's {@code JsonSerializer} embeds a type header
 * ({@code __TypeId__}) containing the FULLY QUALIFIED CLASS NAME of the
 * event on the PRODUCER's classpath -
 * {@code com.financetracker.ingestion.dto.event.StatementIngestedEvent}.
 * That class doesn't exist on parsing-service's classpath (this service
 * has its own, separately-defined mirror of the same shape in package
 * {@code com.financetracker.parsing.dto.event}). A type-header-driven
 * {@code JsonDeserializer} would fail outright trying to load a class
 * that isn't there. Consuming as a raw {@code String} and parsing it
 * ourselves into OUR OWN {@link StatementIngestedEvent} class sidesteps
 * that cross-service classpath mismatch entirely - this is a real,
 * commonly-hit gotcha in event-driven microservices without a shared
 * schema registry, worth understanding rather than configuring around
 * blindly.
 *
 * <p><b>Manual acknowledgment + manual dead-lettering:</b> the listener
 * container is configured with {@code ack-mode: manual}
 * (application.yml), so nothing is marked "consumed" until we explicitly
 * call {@link Acknowledgment#acknowledge()}. If processing fails for a
 * reason unlikely to succeed on a bare retry (e.g. malformed JSON, or
 * the LLM consistently failing to parse a particular chunk), we forward
 * the RAW original payload to the dead-letter topic and STILL acknowledge
 * it - deliberately choosing "never block the whole consumer group on
 * one poison message" over "retry forever", since a human can always
 * replay a dead-lettered message once the root cause is fixed.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class StatementIngestedListener {

    private final TransactionParsingService transactionParsingService;
    private final KafkaTemplate<String, String> deadLetterKafkaTemplate;

    @Value("${finance-tracker.kafka.topics.statement-ingested-dlt}")
    private String dltTopic;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @KafkaListener(
            topics = "${finance-tracker.kafka.topics.statement-ingested}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onStatementIngested(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String rawPayload = record.value();
        try {
            StatementIngestedEvent event = objectMapper.readValue(rawPayload, StatementIngestedEvent.class);
            transactionParsingService.processStatement(event);
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to process statement.ingested message at offset={} partition={} - sending to DLT",
                    record.offset(), record.partition(), ex);
            sendToDeadLetter(record, rawPayload);
            // Acknowledge even on failure - see class javadoc for why we
            // choose "dead-letter and move on" over "retry forever".
            ack.acknowledge();
        }
    }

    private void sendToDeadLetter(ConsumerRecord<String, String> originalRecord, String rawPayload) {
        try {
            deadLetterKafkaTemplate.send(dltTopic, originalRecord.key(), rawPayload).get();
        } catch (Exception dltEx) {
            // If we can't even reach the dead-letter topic, this is a
            // genuinely exceptional situation (likely Kafka itself is
            // unhealthy) - log loudly rather than losing the failure
            // silently. The original message is still safe on the
            // source topic's log at this offset if manual replay is
            // needed later.
            log.error("Failed to publish to dead-letter topic {} for key={} - message may need manual recovery",
                    dltTopic, originalRecord.key(), dltEx);
        }
    }
}
