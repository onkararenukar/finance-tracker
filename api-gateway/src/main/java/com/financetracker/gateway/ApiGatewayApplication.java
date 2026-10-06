package com.financetracker.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the API Gateway - the single public entrypoint for the
 * platform.
 *
 * <p>Two responsibilities:
 * <ol>
 *   <li>Routes REST calls to ingestion-service and categorization-service
 *       (see application.yml's {@code spring.cloud.gateway.routes}).</li>
 *   <li>Hosts {@code ws://.../ws/kafka-events}, relaying live Kafka
 *       traffic metadata to the dashboard's live data-flow visualization
 *       page (see the {@code kafka} and {@code websocket} packages).</li>
 * </ol>
 *
 * <p>Runs on Netty/WebFlux (via Spring Cloud Gateway), unlike every
 * other service in this project which runs on Tomcat/Servlet - see this
 * module's pom.xml javadoc.
 */
@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
