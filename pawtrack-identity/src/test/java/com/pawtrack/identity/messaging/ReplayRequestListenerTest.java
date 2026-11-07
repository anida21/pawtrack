package com.pawtrack.identity.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.pawtrack.identity.support.IntegrationTest;
import com.pawtrack.identity.support.TestUser;
import com.pawtrack.identity.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/** Backfill (events.md): a replay.requested message makes identity republish users and memberships. */
class ReplayRequestListenerTest extends IntegrationTest {

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Test
    void republishesUsersAndMembershipsOnRequest() throws Exception {
        TestUser alice = TestUser.random("Alice");
        TestUser bob = TestUser.random("Bob");
        signIn(alice);
        String householdId = createHousehold(alice, "Kuća");
        join(alice, householdId, bob, "SITTER");
        Instant requestedAt = Instant.now();

        requestReplay("[\"user.updated\",\"household.member.added\"]");

        var user = awaitEvent("user.updated", after(requestedAt).and(data("userId", alice.id())));
        assertThat(user.read("$.data.email", String.class)).isEqualTo(alice.email());
        var sitter = awaitEvent("household.member.added", after(requestedAt)
            .and(data("householdId", householdId)).and(data("userId", bob.id())));
        assertThat(sitter.read("$.data.role", String.class)).isEqualTo("SITTER");
        assertThat(sitter.read("$.producer", String.class)).isEqualTo("pawtrack-identity");
    }

    @Test
    void onlyRequestedTypesAreReplayed() throws Exception {
        TestUser carol = TestUser.random("Carol");
        signIn(carol);
        Instant requestedAt = Instant.now();

        requestReplay("[\"household.member.added\"]");

        awaitEvent("household.member.added", after(requestedAt).and(data("userId", carol.id())));
        assertThat(drainTypesSince(requestedAt)).doesNotContain("user.updated");
    }

    /** Only events created after {@code instant}, so live events from the test setup never count as replayed. */
    private static Predicate<DocumentContext> after(Instant instant) {
        return event -> !Instant.parse(event.read("$.occurredAt", String.class)).isBefore(instant);
    }

    private List<String> drainTypesSince(Instant instant) {
        List<String> types = new ArrayList<>();
        Message message;
        while ((message = rabbitTemplate.receive(TestcontainersConfiguration.CAPTURE_QUEUE, 1000)) != null) {
            DocumentContext event = JsonPath.parse(new String(message.getBody(), StandardCharsets.UTF_8));
            if (after(instant).test(event)) {
                types.add(event.read("$.type", String.class));
            }
        }
        return types;
    }

    private void requestReplay(String typesJson) {
        String body = """
            {"eventId":"%s","type":"replay.requested","occurredAt":"%s",
             "producer":"pawtrack-test","data":{"requestedBy":"pawtrack-test","types":%s}}
            """.formatted(UUID.randomUUID(), Instant.now(), typesJson);
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        // A foreign type header, like real producers send: must not break parsing.
        props.setHeader("__TypeId__", "com.pawtrack.notification.messaging.ReplayRequest");
        rabbitTemplate.send("pawtrack.events", "replay.requested",
            MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8)).andProperties(props).build());
    }
}
