package com.pawtrack.gateway.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** Replaces the Keycloak-backed decoder (Boot's auto-configured one backs off when this bean exists). */
@TestConfiguration(proxyBeanMethods = false)
public class TestJwtDecoderConfig {

    @Bean
    JwtDecoder jwtDecoder() {
        return TestTokens.decoder();
    }
}
