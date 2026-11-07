package com.pawtrack.pet.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.pawtrack.pet.repository.PetRepository;
import com.pawtrack.pet.repository.ProcessedEventRepository;
import com.pawtrack.pet.support.IntegrationTest;
import com.pawtrack.pet.support.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Consumer of {@code household.deleted} on queue {@code pet.household-events} (events.md). */
class HouseholdEventListenerTest extends IntegrationTest {

    private static final String EXCHANGE = "pawtrack.events";
    private static final String DLQ = "pet.household-events.dlq";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    PetRepository petRepository;

    @Autowired
    ProcessedEventRepository processedEvents;

    private final TestUser owner = TestUser.random("Owner");

    @Test
    void softDeletesThePetsOfTheHouseholdAndPublishesPetDeleted() throws Exception {
        UUID doomed = household(owner);
        UUID kept = household(owner);
        String luna = createPet(owner, doomed, "Luna", "DOG");
        String momo = createPet(owner, doomed, "Momo", "CAT");
        String bruno = createPet(owner, kept, "Bruno", "DOG");

        UUID eventId = UUID.randomUUID();
        sendHouseholdDeleted(eventId, doomed);

        await().atMost(TIMEOUT).until(() -> processedEvents.existsById(eventId));
        awaitEvent("pet.deleted", data("petId", luna));
        awaitEvent("pet.deleted", data("petId", momo));
        assertThat(petRepository.findById(UUID.fromString(luna)).orElseThrow().getDeletedAt()).isNotNull();
        assertThat(petRepository.findById(UUID.fromString(momo)).orElseThrow().getDeletedAt()).isNotNull();

        // identity has not removed the membership in this test, so the 404 comes from the soft delete itself
        mvc.perform(get("/api/v1/pets/{id}", luna).with(owner.jwt())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/pets/{id}", bruno).with(owner.jwt())).andExpect(status().isOk());
    }

    @Test
    void redeliveredEventIsIgnored() throws Exception {
        UUID household = household(owner);
        createPet(owner, household, "Luna", "DOG");
        UUID eventId = UUID.randomUUID();
        sendHouseholdDeleted(eventId, household);
        await().atMost(TIMEOUT).until(() -> processedEvents.existsById(eventId));

        // A pet created afterwards must survive a redelivery of the same event.
        String later = createPet(owner, household, "Later", "CAT");
        sendHouseholdDeleted(eventId, household);
        // Messages are consumed in order: once a marker event is processed, the redelivery has been handled.
        UUID marker = UUID.randomUUID();
        sendHouseholdDeleted(marker, UUID.randomUUID());
        await().atMost(TIMEOUT).until(() -> processedEvents.existsById(marker));

        assertThat(petRepository.findById(UUID.fromString(later)).orElseThrow().getDeletedAt()).isNull();
    }

    @Test
    void unreadableMessagesGoToTheDeadLetterQueue() {
        String garbage = "not json " + UUID.randomUUID();
        rabbitTemplate.send(EXCHANGE, HouseholdDeletedEvent.TYPE, json(garbage));
        String invalid = """
            {"eventId":"%s","type":"household.deleted","data":{}}""".formatted(UUID.randomUUID());
        rabbitTemplate.send(EXCHANGE, HouseholdDeletedEvent.TYPE, json(invalid));

        assertThat(receiveFromDlq()).isEqualTo(garbage);
        assertThat(receiveFromDlq()).isEqualTo(invalid);
    }

    private void sendHouseholdDeleted(UUID eventId, UUID householdId) {
        String body = """
            {"eventId":"%s","type":"household.deleted","occurredAt":"%s","producer":"pawtrack-identity",
             "data":{"householdId":"%s"}}""".formatted(eventId, Instant.now(), householdId);
        rabbitTemplate.send(EXCHANGE, HouseholdDeletedEvent.TYPE, json(body));
    }

    private static Message json(String body) {
        return MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8))
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .build();
    }

    private String receiveFromDlq() {
        Message message = rabbitTemplate.receive(DLQ, TIMEOUT.toMillis());
        assertThat(message).as("message in " + DLQ).isNotNull();
        return new String(message.getBody(), StandardCharsets.UTF_8);
    }
}
