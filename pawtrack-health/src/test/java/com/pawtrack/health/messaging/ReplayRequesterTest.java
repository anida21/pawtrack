package com.pawtrack.health.messaging;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.pawtrack.health.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/** Backfill (events.md): health asks pet to republish every pet. */
class ReplayRequesterTest extends IntegrationTest {

    private static final String QUEUE = "health.test.replay-requests";

    @Autowired
    ReplayRequester requester;

    @Autowired
    AmqpAdmin amqpAdmin;

    @Test
    void publishesReplayRequestedForPets() {
        Queue queue = new Queue(QUEUE, true); // RabbitMQ 4 rejects transient non-exclusive queues
        amqpAdmin.declareQueue(queue);
        amqpAdmin.declareBinding(BindingBuilder.bind(queue).to(new TopicExchange("pawtrack.events"))
            .with("replay.requested"));
        amqpAdmin.purgeQueue(QUEUE, false);

        requester.requestReplay();

        Message message = rabbitTemplate.receive(QUEUE, 10_000);
        assertThat(message).isNotNull();
        DocumentContext event = JsonPath.parse(new String(message.getBody(), StandardCharsets.UTF_8));
        assertThat(event.read("$.type", String.class)).isEqualTo("replay.requested");
        assertThat(event.read("$.data.requestedBy", String.class)).isEqualTo("pawtrack-health");
        assertThat(event.read("$.data.types", List.class)).containsExactly("pet.updated");
    }
}
