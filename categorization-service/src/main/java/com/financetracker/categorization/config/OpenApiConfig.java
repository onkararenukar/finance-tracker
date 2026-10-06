package com.financetracker.categorization.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI financeTrackerCategorizationOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Finance Tracker Categorization Service")
                        .description("Service for categorizing transactions using LLM and managing transaction categories")
                        .version("1.0.0")
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8083").description("Local Development Server")
                ));
    }
}