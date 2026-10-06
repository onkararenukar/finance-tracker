package com.financetracker.gateway.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financetracker.gateway.kafka.KafkaLiveEventBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

/**
 * Serves {@code ws://.../ws/kafka-events}. Every connected client
 * receives a JSON text frame for each {@link
 * com.financetracker.gateway.model.KafkaLiveEvent} published to
 * {@link KafkaLiveEventBroadcaster} from the moment they connect.
 *
 * <p>Uses the REACTIVE WebSocket API
 * ({@code org.springframework.web.reactive.socket.WebSocketHandler}),
 * not Spring's more commonly seen servlet-based
 * {@code org.springframework.web.socket.WebSocketHandler} - this whole
 * module runs on Netty via Spring Cloud Gateway/WebFlux, which has no
 * servlet container to hang a traditional {@code @ServerEndpoint} or
 * {@code WebSocketConfigurer} off of.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class KafkaLiveEventWebSocketHandler implements WebSocketHandler {

    private final KafkaLiveEventBroadcaster broadcaster;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules(); // registers the JSR-310 module for Instant serialization

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        log.info("Live-view client connected: {}", session.getId());

        var outbound = broadcaster.eventStream()
                .map(this::toJson)
                .map(session::textMessage)
                .doOnCancel(() -> log.info("Live-view client disconnected: {}", session.getId()));

        return session.send(outbound);
    }

    private String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.error("Failed to serialize live event to JSON", e);
            return "{}";
        }
    }
}
