package com.financetracker.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Analytics Service - read-only aggregation queries
 * over transactions/categories owned by parsing-service and
 * categorization-service. See this module's pom.xml for why this
 * service is a deliberate, narrow exception to the "each service owns
 * its own tables" rule followed everywhere else in this project.
 */
@SpringBootApplication
public class AnalyticsServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AnalyticsServiceApplication.class, args);
    }
}
