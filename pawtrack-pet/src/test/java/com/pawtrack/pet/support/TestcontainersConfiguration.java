package com.pawtrack.pet.support;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real Postgres + RabbitMQ + MinIO for every Spring test. Import it so all tests share one cached context
 * (and therefore one set of containers). Identity is replaced by {@link FakeIdentity}. Requires Docker.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /**
     * Test-only queue that receives every event published to {@code pawtrack.events}.
     * Durable because RabbitMQ 4 rejects transient non-exclusive queues; the container is thrown away anyway.
     */
    public static final String CAPTURE_QUEUE = "pet.test.captured-events";

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

    /**
     * {@code minio/minio} is no longer published on Docker Hub, so Chainguard's MinIO build is used
     * (it only offers {@code latest} for free). It runs as a non-root user that cannot write the
     * container's data directory, hence root inside this throw-away container.
     */
    @Bean
    MinIOContainer minio() {
        DockerImageName image = DockerImageName.parse("cgr.dev/chainguard/minio:latest")
            .asCompatibleSubstituteFor("minio/minio");
        return new MinIOContainer(image).withCreateContainerCmdModifier(cmd -> cmd.withUser("0"));
    }

    /** No @ServiceConnection exists for MinIO: point both endpoints at the container. */
    @Bean
    DynamicPropertyRegistrar minioProperties(MinIOContainer minio) {
        return registry -> {
            registry.add("pawtrack.photos.endpoint", minio::getS3URL);
            registry.add("pawtrack.photos.public-endpoint", minio::getS3URL);
            registry.add("pawtrack.photos.access-key", minio::getUserName);
            registry.add("pawtrack.photos.secret-key", minio::getPassword);
        };
    }

    @Bean
    @Primary
    FakeIdentity fakeIdentity() {
        return new FakeIdentity();
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
