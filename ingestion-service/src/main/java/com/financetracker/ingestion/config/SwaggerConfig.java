package com.financetracker.ingestion.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes interactive API docs at {@code /swagger-ui.html} and the raw
 * OpenAPI spec at {@code /v3/api-docs} - useful both for manually testing
 * uploads during development and as a machine-readable contract the
 * frontend/API-gateway can generate clients from later.
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Finance Tracker - Ingestion Service")
                        .version("v1")
                        .description("Accepts multi-bank statement uploads (PDF/CSV), extracts raw text, " +
                                "and publishes statement.ingested Kafka events for downstream parsing."));
    }
}
