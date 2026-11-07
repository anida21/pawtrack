package com.pawtrack.notification;

import java.util.Properties;

import com.pawtrack.notification.config.RabbitConfig;
import com.pawtrack.notification.support.IntegrationTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the whole service against real Postgres + RabbitMQ + Mailpit (Testcontainers)
 * and runs the Liquibase migrations. Requires Docker.
 */
class NotificationApplicationTests extends IntegrationTest {

    @Autowired
    HealthEndpoint health;

    @Autowired
    AmqpAdmin amqpAdmin;

    @Test
    void contextLoadsAndMigrationsRun() {
        assertThat(health.health().getStatus().getCode()).isEqualTo("UP");
    }

    @ParameterizedTest
    @ValueSource(strings = {RabbitConfig.IDENTITY_QUEUE, RabbitConfig.PET_QUEUE, RabbitConfig.HEALTH_QUEUE})
    void consumerQueuesAndTheirDeadLetterQueuesAreDeclared(String queue) {
        Properties consumer = amqpAdmin.getQueueProperties(queue);
        assertThat(consumer).isNotNull();
        assertThat(consumer.get("QUEUE_CONSUMER_COUNT")).isEqualTo(1);
        assertThat(amqpAdmin.getQueueProperties(queue + ".dlq")).isNotNull();
    }

    @Test
    void healthAndApiDocsArePublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }
}
