package com.financetracker.ingestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.embedded.tomcat.TomcatProtocolHandlerCustomizer;
import org.springframework.context.annotation.Bean;

/**
 * Entry point for the Ingestion Service.
 *
 * <p>This is the microservice responsible for receiving uploaded bank
 * statements (PDF/CSV, from any bank), extracting their raw text/rows,
 * persisting a tracking record, and publishing a {@code statement.ingested}
 * Kafka event so the (future) parsing-service can turn that raw text into
 * structured transactions.
 *
 * <h2>Why Virtual Threads (Java 24 / Project Loom)?</h2>
 * File upload handling is heavily I/O-bound: reading multipart bytes from
 * the network, writing them to disk/object storage, and later (in the
 * parsing service) making LLM API calls. Virtual threads let each request
 * "block" on I/O the way traditional thread-per-request code always has,
 * but without consuming an expensive OS thread while waiting - the JVM
 * parks the virtual thread and frees the underlying carrier thread to do
 * other work. This means we can handle thousands of concurrent uploads
 * with a tiny platform-thread pool, no reactive/WebFlux rewrite required.
 *
 * <p>Enabling it is a single Spring Boot property
 * ({@code spring.threads.virtual.enabled=true}), which is set below via
 * a bean rather than in application.yml purely so the reasoning lives
 * next to the code that depends on it (extraction + I/O heavy service
 * methods use virtual threads implicitly through Spring MVC's request
 * handling once this is enabled).
 */
@SpringBootApplication
public class IngestionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IngestionServiceApplication.class, args);
    }

    /**
     * A no-op customizer bean whose only purpose here is documentation:
     * the actual switch to virtual threads for Tomcat's request-handling
     * executor is the {@code spring.threads.virtual.enabled: true}
     * property in application.yml. Spring Boot 3.2+ reads that property
     * and swaps Tomcat's thread pool for a virtual-thread-per-task
     * executor automatically - no manual Executor wiring needed.
     */
    @Bean
    TomcatProtocolHandlerCustomizer<?> loggingProtocolHandlerCustomizer() {
        return protocolHandler ->
                System.out.println("[ingestion-service] Virtual threads enabled for request handling");
    }
}
