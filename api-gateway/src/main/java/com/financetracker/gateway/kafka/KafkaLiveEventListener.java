package com.financetracker.gateway.kafka;

import com.financetracker.gateway.model.KafkaLiveEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Observes every message on the topics listed in
 * {@code finance-tracker.kafka.live-view-topics} and relays lightweight
 * metadata about each one to {@link KafkaLiveEventBroadcaster}.
 *
 * <p><b>Deliberately a separate, dedicated consumer group</b>
 * ({@code api-gateway-live-view}, see application.yml) from
 * parsing-service's and categorization-service's real processing
 * consumer groups. Kafka delivers every message to every DISTINCT
 * consumer group independently - this listener merely OBSERVES traffic
 * for visualization and never competes for partition assignment or
 * affects the actual processing pipeline's consumer lag in any way.
 *
 * <p><b>{@code auto-offset-reset: latest}</b> (see application.yml) - on
 * first startup (or after being offline a while), this listener
 * intentionally does NOT replay historical messages. A live view should
 * show what's happening NOW, not dump a backlog of old traffic onto
 * every dashboard the moment it connects.
 */
@Component
@RequiredArgsConstructor
public class KafkaLiveEventListener {

    private final KafkaLiveEventBroadcaster broadcaster;

    @KafkaListener(
            topics = "#{'${finance-tracker.kafka.live-view-topics}'.split(',')}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onMessage(ConsumerRecord<String, String> record) {
        var event = new KafkaLiveEvent(
                record.topic(),
                record.partition(),
                record.offset(),
                record.key(),
                record.value() == null ? 0 : record.value().getBytes().length,
                Instant.ofEpochMilli(record.timestamp()),
                Instant.now()
        );
        broadcaster.publish(event);
    }
}
