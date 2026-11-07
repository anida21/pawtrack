package com.pawtrack.gateway.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** CORS for browser clients (the Angular UI). Bound from {@code pawtrack.cors.*}. */
@ConfigurationProperties("pawtrack.cors")
public record CorsProperties(
    List<String> allowedOrigins,
    List<String> allowedMethods,
    List<String> allowedHeaders,
    List<String> exposedHeaders,
    Duration maxAge) {
}
