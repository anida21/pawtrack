package com.pawtrack.pet.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * HTTP client for identity. A plain blocking request factory with short timeouts: the membership lookup
 * is one small GET per user per minute, and a slow identity should fail fast (503) rather than hold
 * request threads. Built with {@code RestClient.builder()} so no JDK {@code HttpClient} (NIO pipe) is created.
 */
@Configuration
public class IdentityClientConfig {

    @Bean
    RestClient identityRestClient(PawtrackProperties properties) {
        PawtrackProperties.Identity identity = properties.identity();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(identity.connectTimeout());
        requestFactory.setReadTimeout(identity.readTimeout());
        return RestClient.builder().baseUrl(identity.url()).requestFactory(requestFactory).build();
    }
}
