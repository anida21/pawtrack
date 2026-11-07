package com.pawtrack.gateway.support;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base class for gateway tests: full application with MockMvc, every service replaced by a {@link StubBackend}
 * (wired through the same env vars docker-compose uses), Keycloak replaced by {@link TestTokens}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtDecoderConfig.class)
public abstract class IntegrationTest {

    protected static final StubBackend IDENTITY = StubBackend.start("identity");
    protected static final StubBackend PET = StubBackend.start("pet");
    protected static final StubBackend HEALTH = StubBackend.start("health");
    protected static final StubBackend NOTIFICATION = StubBackend.start("notification");

    protected static final List<StubBackend> BACKENDS = List.of(IDENTITY, PET, HEALTH, NOTIFICATION);

    @Autowired
    protected MockMvc mvc;

    @DynamicPropertySource
    static void backendUrls(DynamicPropertyRegistry registry) {
        registry.add("IDENTITY_URL", IDENTITY::url);
        registry.add("PET_URL", PET::url);
        registry.add("HEALTH_URL", HEALTH::url);
        registry.add("NOTIFICATION_URL", NOTIFICATION::url);
    }

    @BeforeEach
    void resetBackends() {
        BACKENDS.forEach(StubBackend::reset);
    }

    protected static StubBackend backend(String name) {
        return BACKENDS.stream().filter(b -> b.name().equals(name)).findFirst().orElseThrow();
    }
}
