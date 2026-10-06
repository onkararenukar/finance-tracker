package com.financetracker.gateway.config;

import com.financetracker.gateway.websocket.KafkaLiveEventWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.Map;

/**
 * Wires {@link KafkaLiveEventWebSocketHandler} to the
 * {@code /ws/kafka-events} path, and registers the adapter WebFlux needs
 * to actually upgrade matching HTTP requests to WebSocket connections.
 *
 * <p>This is the reactive-stack equivalent of the more commonly seen
 * {@code @EnableWebSocket} + {@code WebSocketConfigurer} pattern from
 * servlet-based Spring MVC apps - see the handler class's javadoc for
 * why that servlet-based approach doesn't apply in this Netty-based module.
 */
@Configuration
@RequiredArgsConstructor
public class WebSocketConfig {

    private final KafkaLiveEventWebSocketHandler kafkaLiveEventWebSocketHandler;

    @Bean
    public HandlerMapping webSocketHandlerMapping() {
        var mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(Map.of("/ws/kafka-events", (WebSocketHandler) kafkaLiveEventWebSocketHandler));
        // Higher priority than Spring Cloud Gateway's own routing
        // handler mapping, so a WebSocket upgrade request for this exact
        // path is claimed here instead of falling through to (and being
        // rejected by) the HTTP proxying route matcher.
        mapping.setOrder(-1);
        return mapping;
    }

    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
