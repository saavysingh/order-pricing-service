package com.example.orderservice.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class DemoCorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;
    private final String[] allowedOriginPatterns;

        public DemoCorsConfig(
            @Value("${demo.cors.allowed-origins:http://localhost:5173,http://localhost:3000,http://localhost:8081}") String allowedOrigins,
            @Value("${demo.cors.allowed-origin-patterns:https://*.lovable.app}") String allowedOriginPatterns
        ) {
        this.allowedOrigins = allowedOrigins.split(",");
        this.allowedOriginPatterns = allowedOriginPatterns.split(",");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/demo/**")
            .allowedOrigins(allowedOrigins)
            .allowedOriginPatterns(allowedOriginPatterns)
            .allowedMethods("GET", "POST", "OPTIONS")
            .allowedHeaders("Content-Type", "Idempotency-Key")
            .allowCredentials(false)
            .maxAge(3600);

        registry.addMapping("/v1/**")
            .allowedOrigins(allowedOrigins)
            .allowedOriginPatterns(allowedOriginPatterns)
            .allowedMethods("GET", "POST", "OPTIONS")
            .allowedHeaders("Content-Type", "Idempotency-Key")
            .allowCredentials(false)
            .maxAge(3600);
    }
}