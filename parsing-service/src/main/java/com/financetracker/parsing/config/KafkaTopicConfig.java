package com.financetracker.parsing.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the Kafka topics THIS service is responsible for creating.
 *
 * <p>Note we deliberately do NOT declare a {@code NewTopic} bean for
 * {@code statement.ingested} here - that topic is owned and created by
 * ingestion-service (its producer). Each service should only own the
 * topics it produces to; consumer-only services shouldn't presume to
 * define partition counts for a topic another service is authoritative
 * over.
 */
@Configuration
public class KafkaTopicConfig {

    @Value("${finance-tracker.kafka.topics.transaction-parsed}")
    private String transactionParsedTopic;

    @Value("${finance-tracker.kafka.topics.statement-ingested-dlt}")
    private String statementIngestedDltTopic;

    @Bean
    public NewTopic transactionParsedTopic() {
        return TopicBuilder.name(transactionParsedTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic statementIngestedDltTopic() {
        // Single partition is fine for a dead-letter topic - volume
        // should be low (ideally zero), and we're optimizing for easy
        // manual inspection (kafka-console-consumer / Kafka UI) over
        // throughput here.
        return TopicBuilder.name(statementIngestedDltTopic)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
