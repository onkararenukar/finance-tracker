package com.financetracker.categorization.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/** Dead-letter topic + producer, same pattern as parsing-service's KafkaProducerConfig for its DLT. */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${finance-tracker.kafka.topics.transaction-parsed-dlt}")
    private String dltTopicName;

    @Bean
    public NewTopic transactionParsedDltTopic() {
        return TopicBuilder.name(dltTopicName).partitions(1).replicas(1).build();
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
