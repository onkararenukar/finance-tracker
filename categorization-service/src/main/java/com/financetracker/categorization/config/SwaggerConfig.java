package com.financetracker.categorization.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
                .title("Finance Tracker - Categorization Service")
                .version("v1")
                .description("Categorizes transactions via LLM, flagging unrecognized categories for review."));
    }
}
