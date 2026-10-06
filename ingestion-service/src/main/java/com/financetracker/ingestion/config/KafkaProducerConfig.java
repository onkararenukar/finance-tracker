package com.financetracker.ingestion.config;

import com.financetracker.ingestion.dto.event.StatementIngestedEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Declares an explicitly-typed {@code KafkaTemplate<String, StatementIngestedEvent>}
 * bean.
 *
 * <p>Why this exists instead of relying on Spring Boot's auto-configured
 * {@code KafkaTemplate}: Spring Boot's autoconfiguration produces a
 * {@code KafkaTemplate<Object, Object>} bean. Because of Java generic type
 * erasure, injecting that bean into a field/constructor parameter typed
 * as {@code KafkaTemplate<String, StatementIngestedEvent>} either fails
 * to autowire cleanly or silently loses type safety, depending on Spring
 * version behavior - a classic, easy-to-miss gotcha with typed Kafka
 * templates. Defining our own bean with the exact generic type we want
 * to inject elsewhere (see {@code StatementIngestionService}) sidesteps
 * the ambiguity entirely and gives us compile-time type safety on every
 * {@code kafkaTemplate.send(...)} call.
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, StatementIngestedEvent> statementEventProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // Wait for all in-sync replicas before acking - never silently lose an ingestion event.
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        // Idempotent producer: Kafka dedupes retried sends by sequence number,
        // so a transient retry after a broker blip can never double-publish.
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, StatementIngestedEvent> statementEventKafkaTemplate(
            ProducerFactory<String, StatementIngestedEvent> statementEventProducerFactory) {
        return new KafkaTemplate<>(statementEventProducerFactory);
    }
}
