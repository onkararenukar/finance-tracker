package com.financetracker.gateway.kafka;

import com.financetracker.gateway.model.KafkaLiveEvent;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Instant;

class KafkaLiveEventBroadcasterTest {

    @Test
    void publishedEvents_areDeliveredToSubscriber() {
        var broadcaster = new KafkaLiveEventBroadcaster();
        var event = new KafkaLiveEvent(
                "statement.ingested", 0, 42L, "1",
                256, Instant.now(), Instant.now());

        // Subscribe first (as a real WebSocket client would on connect),
        // THEN publish - this is a multicast hub, not a replay log, so
        // an event published before any subscriber exists is correctly
        // never seen by a subscriber that connects afterward.
        StepVerifier.create(broadcaster.eventStream().take(1))
                .then(() -> broadcaster.publish(event))
                .expectNext(event)
                .verifyComplete();
    }
}
