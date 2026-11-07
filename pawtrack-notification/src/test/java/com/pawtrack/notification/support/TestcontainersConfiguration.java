package com.pawtrack.notification.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/**
 * Real Postgres + RabbitMQ + Mailpit for every Spring test. Import it so all tests share one cached context
 * (and therefore one set of containers). Requires Docker.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    static final int SMTP_PORT = 1025;
    static final int MAILPIT_API_PORT = 8025;

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:17-alpine");
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbit() {
        return new RabbitMQContainer("rabbitmq:4-management-alpine");
    }

    @Bean
    GenericContainer<?> mailpitContainer() {
        return new GenericContainer<>("axllent/mailpit:latest")
            .withExposedPorts(SMTP_PORT, MAILPIT_API_PORT)
            .waitingFor(Wait.forHttp("/livez").forPort(MAILPIT_API_PORT));
    }

    @Bean
    DynamicPropertyRegistrar mailProperties(GenericContainer<?> mailpitContainer) {
        return registry -> {
            registry.add("spring.mail.host", mailpitContainer::getHost);
            registry.add("spring.mail.port", () -> mailpitContainer.getMappedPort(SMTP_PORT));
        };
    }

    @Bean
    Mailpit mailpit(GenericContainer<?> mailpitContainer) {
        return new Mailpit(mailpitContainer);
    }
}
