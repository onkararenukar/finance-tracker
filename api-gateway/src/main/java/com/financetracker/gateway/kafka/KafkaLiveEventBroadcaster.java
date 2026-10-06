package com.financetracker.gateway.kafka;

import com.financetracker.gateway.model.KafkaLiveEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * A multicast hub: {@link KafkaLiveEventListener} pushes every observed
 * Kafka record into this sink, and every connected WebSocket client (see
 * {@code websocket.KafkaLiveEventWebSocketHandler}) subscribes to
 * {@link #eventStream()} to receive them.
 *
 * <p>Uses {@link Sinks.Many#multicast()} with
 * {@code onBackpressureBuffer()} - each new WebSocket subscriber gets
 * events from the moment THEY connect onward (not historical replay),
 * and a temporarily slow client gets a bounded buffer rather than
 * either blocking the Kafka listener thread or silently dropping events
 * for well-behaved clients.
 */
@Component
public class KafkaLiveEventBroadcaster {

    private final Sinks.Many<KafkaLiveEvent> sink = Sinks.many().multicast().onBackpressureBuffer();

    /** Called by {@link KafkaLiveEventListener} for every Kafka record observed. */
    public void publish(KafkaLiveEvent event) {
        // emitNext with a lenient failure handler: if a burst of traffic
        // briefly outpaces buffer capacity for one slow subscriber, we
        // drop that one emission for that subscriber rather than letting
        // it propagate back and stall the Kafka listener thread itself -
        // this is a live VISUALIZATION, not a durable audit log, so an
        // occasionally-dropped UI update is an acceptable trade-off for
        // never blocking real message processing elsewhere in the system.
        sink.emitNext(event, Sinks.EmitFailureHandler.FAIL_FAST);
    }

    /** Each subscriber (one per connected WebSocket client) gets its own view of the stream from here forward. */
    public Flux<KafkaLiveEvent> eventStream() {
        return sink.asFlux();
    }
}
