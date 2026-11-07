package com.pawtrack.identity;

import com.pawtrack.identity.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the whole service against real Postgres + RabbitMQ (Testcontainers)
 * and runs the Liquibase migrations. Requires Docker.
 */
class IdentityApplicationTests extends IntegrationTest {

    @Autowired
    HealthEndpoint health;

    @Test
    void contextLoadsAndMigrationsRun() {
        assertThat(health.health().getStatus().getCode()).isEqualTo("UP");
    }
}
