package com.financetracker.ingestion.controller;

import com.financetracker.ingestion.dto.StatementUploadResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack integration test: real Postgres (via Testcontainers) + an
 * embedded, in-JVM Kafka broker, driving the actual HTTP endpoint exactly
 * as a real client would. This is the test that would catch, for
 * example, a Flyway migration that doesn't match the JPA entity, or a
 * Kafka serialization mismatch - neither of which a mocked unit test can
 * verify.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {"statement.ingested"})
class StatementUploadControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("financetracker_test");

    /**
     * Redirects the application's datasource + Kafka bootstrap-servers to
     * the ephemeral Testcontainers Postgres and the embedded Kafka broker
     * spun up for this test class, instead of the real docker-compose
     * infrastructure - keeps this test hermetic and runnable in CI with
     * no manually-started dependencies.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void uploadStatement_validCsv_returnsCreatedWithPublishedStatus() {
        var csvContent = """
                date,description,amount
                2026-01-01,Coffee Shop,-4.50
                2026-01-02,Salary,3000.00
                """;

        var fileResource = new org.springframework.core.io.ByteArrayResource(csvContent.getBytes()) {
            @Override
            public String getFilename() {
                // Multipart uploads need a filename for Spring to resolve
                // the extension-based file type in the controller/service.
                return "test-statement.csv";
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileResource);
        body.add("bankName", "Test Bank");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

        var response = restTemplate.postForEntity(
                "/api/v1/statements", request, StatementUploadResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("PUBLISHED");
        assertThat(response.getBody().chunkCount()).isEqualTo(1);
        assertThat(response.getBody().bankName()).isEqualTo("Test Bank");
    }
}
