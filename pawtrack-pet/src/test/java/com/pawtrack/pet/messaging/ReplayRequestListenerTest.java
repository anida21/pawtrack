package com.pawtrack.pet.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.pawtrack.pet.support.IntegrationTest;
import com.pawtrack.pet.support.TestUser;
import com.pawtrack.pet.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Backfill (events.md): replay.requested with pet.updated makes pet republish every pet that is not deleted. */
class ReplayRequestListenerTest extends IntegrationTest {

    @Test
    void republishesLivePetsAsPetUpdated() throws Exception {
        TestUser owner = TestUser.random("Owner");
        UUID householdId = household(owner);
        String luna = createPet(owner, householdId, "Luna", "DOG");
        String gone = createPet(owner, householdId, "Gone", "CAT");
        mvc.perform(delete("/api/v1/pets/{id}", gone).with(owner.jwt())).andExpect(status().isNoContent());
        Instant requestedAt = Instant.now();

        requestReplay("[\"pet.updated\"]");

        var event = awaitEvent("pet.updated", after(requestedAt).and(data("petId", luna)));
        assertThat(event.read("$.data.householdId", String.class)).isEqualTo(householdId.toString());
        assertThat(event.read("$.data.name", String.class)).isEqualTo("Luna");
        assertThat(event.read("$.data.species", String.class)).isEqualTo("DOG");
        assertThat(drainSince(requestedAt, "pet.updated")).doesNotContain(gone);
    }

    @Test
    void ignoresRequestsForOtherTypes() throws Exception {
        TestUser owner = TestUser.random("Owner");
        createPet(owner, household(owner), "Rex", "DOG");
        Instant requestedAt = Instant.now();

        requestReplay("[\"user.updated\"]");

        assertThat(drainSince(requestedAt, "pet.updated")).isEmpty();
    }

    private static Predicate<DocumentContext> after(Instant instant) {
        return event -> !Instant.parse(event.read("$.occurredAt", String.class)).isBefore(instant);
    }

    /** Pet ids of {@code type} events published since {@code instant}, until the capture queue is quiet. */
    private List<String> drainSince(Instant instant, String type) {
        List<String> petIds = new ArrayList<>();
        Message message;
        while ((message = rabbitTemplate.receive(TestcontainersConfiguration.CAPTURE_QUEUE, 1500)) != null) {
            DocumentContext event = JsonPath.parse(new String(message.getBody(), StandardCharsets.UTF_8));
            if (type.equals(event.read("$.type")) && after(instant).test(event)) {
                petIds.add(event.read("$.data.petId", String.class));
            }
        }
        return petIds;
    }

    private void requestReplay(String typesJson) {
        String body = """
            {"eventId":"%s","type":"replay.requested","occurredAt":"%s",
             "producer":"pawtrack-test","data":{"requestedBy":"pawtrack-test","types":%s}}
            """.formatted(UUID.randomUUID(), Instant.now(), typesJson);
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setHeader("__TypeId__", "com.pawtrack.health.messaging.ReplayRequest");
        rabbitTemplate.send("pawtrack.events", "replay.requested",
            MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8)).andProperties(props).build());
    }
}
