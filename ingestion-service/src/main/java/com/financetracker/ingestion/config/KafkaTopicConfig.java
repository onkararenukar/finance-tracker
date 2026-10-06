package com.financetracker.ingestion.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the Kafka topics this service owns/produces to.
 *
 * <p>Registering topics as Spring beans (via {@link TopicBuilder}) means
 * Spring Kafka's {@code KafkaAdmin} creates them automatically on startup
 * if they don't already exist - useful in local/dev (docker-compose also
 * has {@code KAFKA_AUTO_CREATE_TOPICS_ENABLE=true} as a safety net, but
 * explicit topic definitions let us control partition count and
 * replication factor deliberately instead of relying on broker defaults).
 */
@Configuration
public class KafkaTopicConfig {

    @Value("${finance-tracker.kafka.topics.statement-ingested}")
    private String statementIngestedTopic;

    /**
     * 3 partitions: allows up to 3 parsing-service consumer instances to
     * process statements from different partitions in parallel once we
     * scale that service out. Partition key will be the statementId
     * (see StatementIngestionService), which guarantees all events for
     * the same statement land on the same partition and are therefore
     * processed in order relative to each other.
     */
    @Bean
    public NewTopic statementIngestedTopic() {
        return TopicBuilder.name(statementIngestedTopic)
                .partitions(3)
                .replicas(1) // single-broker dev cluster; raise in a real multi-broker deployment
                .build();
    }
}
