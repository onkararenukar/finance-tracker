package com.financetracker.parsing.config;

import com.financetracker.parsing.dto.event.TransactionParsedEvent;
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
 * Two producer beans:
 * <ul>
 *   <li>{@code transactionParsedKafkaTemplate} - strongly typed to
 *       {@link TransactionParsedEvent}, used by
 *       {@code TransactionParsingService} for the happy path. Defined
 *       explicitly (rather than relying on Spring Boot's auto-configured
 *       {@code KafkaTemplate<Object,Object>}) for the same generic-erasure
 *       reason documented on ingestion-service's {@code KafkaProducerConfig}.</li>
 *   <li>{@code deadLetterKafkaTemplate} - plain {@code String} valued,
 *       used by {@code StatementIngestedListener} to forward the RAW,
 *       unparsed JSON payload of a message that failed processing to the
 *       dead-letter topic, byte-for-byte, so nothing about the original
 *       message is lost or reshaped on the way to the DLT.</li>
 * </ul>
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, TransactionParsedEvent> transactionParsedProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, TransactionParsedEvent> transactionParsedKafkaTemplate(
            ProducerFactory<String, TransactionParsedEvent> transactionParsedProducerFactory) {
        return new KafkaTemplate<>(transactionParsedProducerFactory);
    }

    @Bean
    public ProducerFactory<String, String> deadLetterProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> deadLetterKafkaTemplate(
            ProducerFactory<String, String> deadLetterProducerFactory) {
        return new KafkaTemplate<>(deadLetterProducerFactory);
    }
}
