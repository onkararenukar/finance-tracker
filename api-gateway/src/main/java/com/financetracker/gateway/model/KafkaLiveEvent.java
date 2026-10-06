package com.financetracker.gateway.model;

import java.time.Instant;

/**
 * One Kafka record's metadata, shaped for the dashboard's live
 * data-transfer visualization page.
 *
 * <p>Deliberately does NOT include the message's actual payload/value -
 * only metadata (topic, partition, key, size, timing). The live view is
 * meant to visualize FLOW (which topics are busy, how fast messages are
 * moving through the pipeline), not to be a message inspector - showing
 * full payloads (which include transaction descriptions/amounts) over an
 * unauthenticated WebSocket to anything that connects would leak
 * financial data unnecessarily beyond what a flow visualization needs.
 * Kafka UI (already in docker-compose) remains the right tool for
 * inspecting full message content during development.
 */
public record KafkaLiveEvent(
        String topic,
        int partition,
        long offset,
        String key,
        int valueSizeBytes,
        Instant kafkaTimestamp,
        Instant relayedAt
) {
}
