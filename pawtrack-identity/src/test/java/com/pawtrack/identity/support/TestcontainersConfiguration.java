package com.pawtrack.identity.support;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/**
 * Real Postgres + RabbitMQ for every Spring test. Import it so all tests share one cached context
 * (and therefore one pair of containers). Requires Docker.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /**
     * Test-only queue that receives every event published to {@code pawtrack.events}.
     * Durable because RabbitMQ 4 rejects transient non-exclusive queues; the container is thrown away anyway.
     */
    public static final String CAPTURE_QUEUE = "identity.test.captured-events";

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
    Queue capturedEvents() {
        return new Queue(CAPTURE_QUEUE, true);
    }

    @Bean
    Binding capturedEventsBinding(Queue capturedEvents, TopicExchange eventsExchange) {
        return BindingBuilder.bind(capturedEvents).to(eventsExchange).with("#");
    }
}
