package com.pawtrack.notification.messaging;

import java.nio.charset.StandardCharsets;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.pawtrack.notification.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/** Backfill (events.md): notification asks identity and pet to republish users, memberships and pets. */
class ReplayRequesterTest extends IntegrationTest {

    private static final String QUEUE = "notification.test.replay-requests";

    @Autowired
    ReplayRequester requester;

    @Autowired
    AmqpAdmin amqpAdmin;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Test
    void publishesReplayRequestedForEverythingItKeepsACopyOf() {
        Queue queue = new Queue(QUEUE, true); // RabbitMQ 4 rejects transient non-exclusive queues
        amqpAdmin.declareQueue(queue);
        amqpAdmin.declareBinding(BindingBuilder.bind(queue).to(new TopicExchange("pawtrack.events"))
            .with("replay.requested"));
        amqpAdmin.purgeQueue(QUEUE, false);

        requester.requestReplay();

        Message message = rabbitTemplate.receive(QUEUE, 10_000);
        assertThat(message).isNotNull();
        assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
        DocumentContext event = JsonPath.parse(new String(message.getBody(), StandardCharsets.UTF_8));
        assertThat(event.read("$.type", String.class)).isEqualTo("replay.requested");
        assertThat(event.read("$.eventId", String.class)).isNotBlank();
        assertThat(event.read("$.data.requestedBy", String.class)).isEqualTo("pawtrack-notification");
        assertThat(event.read("$.data.types", java.util.List.class))
            .containsExactlyInAnyOrder("user.updated", "household.member.added", "pet.updated");
    }
}
