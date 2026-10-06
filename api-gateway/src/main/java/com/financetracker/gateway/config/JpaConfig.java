package com.financetracker.gateway.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "com.financetracker.gateway.repository")
public class JpaConfig {
}
