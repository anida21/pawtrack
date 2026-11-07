package com.pawtrack.health.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * HTTP client for pet. A plain blocking request factory with short timeouts: a slow pet should fail fast (503)
 * rather than hold request threads. Built with {@code RestClient.builder()} so no JDK {@code HttpClient}
 * (NIO pipe) is created (see CLAUDE.md, local machine note).
 */
@Configuration
public class PetClientConfig {

    @Bean
    RestClient petRestClient(PawtrackProperties properties) {
        PawtrackProperties.Pet pet = properties.pet();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(pet.connectTimeout());
        requestFactory.setReadTimeout(pet.readTimeout());
        return RestClient.builder().baseUrl(pet.url()).requestFactory(requestFactory).build();
    }

    /** "Today" for due dates and active medications is the UTC date (health.yaml). */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
