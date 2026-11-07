package com.pawtrack.health.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.pawtrack.health.repository.MedicationRepository;
import com.pawtrack.health.repository.PetCopyRepository;
import com.pawtrack.health.repository.ProcessedEventRepository;
import com.pawtrack.health.repository.VaccinationRepository;
import com.pawtrack.health.repository.VetVisitRepository;
import com.pawtrack.health.repository.WeightRepository;
import com.pawtrack.health.support.Bodies;
import com.pawtrack.health.support.IntegrationTest;
import com.pawtrack.health.support.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Consumer of {@code pet.created} / {@code pet.deleted} on queue {@code health.pet-events} (events.md). */
class PetEventListenerTest extends IntegrationTest {

    private static final String EXCHANGE = "pawtrack.events";
    private static final String DLQ = "health.pet-events.dlq";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    PetCopyRepository petCopies;

    @Autowired
    ProcessedEventRepository processedEvents;

    @Autowired
    VaccinationRepository vaccinations;

    @Autowired
    VetVisitRepository vetVisits;

    @Autowired
    MedicationRepository medications;

    @Autowired
    WeightRepository weights;

    private final TestUser owner = TestUser.random("Owner");

    @Test
    void petCreatedStoresTheLocalCopy() {
        UUID petId = UUID.randomUUID();
        UUID householdId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        sendPetCreated(eventId, petId, householdId, "Luna");

        await().atMost(TIMEOUT).until(() -> processedEvents.existsById(eventId));
        assertThat(petCopies.findById(petId)).hasValueSatisfying(copy -> {
            assertThat(copy.getHouseholdId()).isEqualTo(householdId);
            assertThat(copy.getName()).isEqualTo("Luna");
            assertThat(copy.isDeleted()).isFalse();
        });
    }

    @Test
    void petDeletedSoftDeletesAllRecordsOfThePet() throws Exception {
        UUID petId = pets.create(owner, "Luna");
        UUID keptPet = pets.create(owner, "Momo");
        UUID householdId = pets.householdOf(petId);
        sendPetCreated(UUID.randomUUID(), petId, householdId, "Luna");
        create(owner, path(petId, "vaccinations"), Bodies.VACCINATION);
        create(owner, path(petId, "vet-visits"), Bodies.VET_VISIT);
        create(owner, path(petId, "medications"), Bodies.MEDICATION);
        create(owner, path(petId, "weights"), Bodies.WEIGHT);
        create(owner, path(keptPet, "vaccinations"), Bodies.VACCINATION);

        UUID eventId = UUID.randomUUID();
        sendPetDeleted(eventId, petId, householdId);

        await().atMost(TIMEOUT).until(() -> processedEvents.existsById(eventId));
        assertThat(petCopies.findById(petId)).hasValueSatisfying(copy -> assertThat(copy.isDeleted()).isTrue());
        assertThat(vaccinations.findAll()).filteredOn(v -> v.getPetId().equals(petId))
            .isNotEmpty().allSatisfy(v -> assertThat(v.getDeletedAt()).isNotNull());
        assertThat(vetVisits.findAll()).filteredOn(v -> v.getPetId().equals(petId))
            .isNotEmpty().allSatisfy(v -> assertThat(v.getDeletedAt()).isNotNull());
        assertThat(medications.findAll()).filteredOn(m -> m.getPetId().equals(petId))
            .isNotEmpty().allSatisfy(m -> assertThat(m.getDeletedAt()).isNotNull());
        assertThat(weights.findAll()).filteredOn(w -> w.getPetId().equals(petId))
            .isNotEmpty().allSatisfy(w -> assertThat(w.getDeletedAt()).isNotNull());
        assertThat(vaccinations.findAll()).filteredOn(v -> v.getPetId().equals(keptPet))
            .allSatisfy(v -> assertThat(v.getDeletedAt()).isNull());

        // FakePets still answers 200: the 404 comes from the local copy being marked deleted.
        mvc.perform(get(path(petId, "vaccinations")).with(owner.jwt())).andExpect(status().isNotFound());
        mvc.perform(get(path(keptPet, "vaccinations")).with(owner.jwt())).andExpect(status().isOk());
    }

    @Test
    void petDeletedBeforePetCreatedLeavesTheCopyDeleted() {
        UUID petId = UUID.randomUUID();
        UUID householdId = UUID.randomUUID();
        UUID created = UUID.randomUUID();

        sendPetDeleted(UUID.randomUUID(), petId, householdId);
        sendPetCreated(created, petId, householdId, "Late");

        await().atMost(TIMEOUT).until(() -> processedEvents.existsById(created));
        assertThat(petCopies.findById(petId)).hasValueSatisfying(copy -> assertThat(copy.isDeleted()).isTrue());
    }

    @Test
    void redeliveredEventIsIgnored() throws Exception {
        UUID petId = pets.create(owner, "Luna");
        UUID householdId = pets.householdOf(petId);
        UUID eventId = UUID.randomUUID();
        sendPetDeleted(eventId, petId, householdId);
        await().atMost(TIMEOUT).until(() -> processedEvents.existsById(eventId));

        // A record of another pet survives a redelivery of the same eventId carrying that pet.
        UUID otherPet = pets.create(owner, "Momo");
        String later = create(owner, path(otherPet, "weights"), Bodies.WEIGHT);
        sendPetDeleted(eventId, otherPet, pets.householdOf(otherPet));
        // Messages are consumed in order: once a marker event is processed, the redelivery has been handled.
        UUID marker = UUID.randomUUID();
        sendPetCreated(marker, UUID.randomUUID(), UUID.randomUUID(), "Marker");
        await().atMost(TIMEOUT).until(() -> processedEvents.existsById(marker));

        assertThat(weights.findById(UUID.fromString(later)).orElseThrow().getDeletedAt()).isNull();
    }

    @Test
    void unreadableOrInvalidMessagesGoToTheDeadLetterQueue() {
        String garbage = "not json " + UUID.randomUUID();
        rabbitTemplate.send(EXCHANGE, PetEventMessage.PET_CREATED, json(garbage));
        String noPetId = """
            {"eventId":"%s","type":"pet.deleted","data":{"householdId":"%s"}}"""
            .formatted(UUID.randomUUID(), UUID.randomUUID());
        rabbitTemplate.send(EXCHANGE, PetEventMessage.PET_DELETED, json(noPetId));
        String noName = """
            {"eventId":"%s","type":"pet.created","data":{"petId":"%s","householdId":"%s"}}"""
            .formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        rabbitTemplate.send(EXCHANGE, PetEventMessage.PET_CREATED, json(noName));

        assertThat(receiveFromDlq()).isEqualTo(garbage);
        assertThat(receiveFromDlq()).isEqualTo(noPetId);
        assertThat(receiveFromDlq()).isEqualTo(noName);
    }

    private void sendPetCreated(UUID eventId, UUID petId, UUID householdId, String name) {
        String body = """
            {"eventId":"%s","type":"pet.created","occurredAt":"%s","producer":"pawtrack-pet",
             "data":{"petId":"%s","householdId":"%s","name":"%s","species":"DOG"}}"""
            .formatted(eventId, Instant.now(), petId, householdId, name);
        rabbitTemplate.send(EXCHANGE, PetEventMessage.PET_CREATED, json(body));
    }

    private void sendPetDeleted(UUID eventId, UUID petId, UUID householdId) {
        String body = """
            {"eventId":"%s","type":"pet.deleted","occurredAt":"%s","producer":"pawtrack-pet",
             "data":{"petId":"%s","householdId":"%s"}}""".formatted(eventId, Instant.now(), petId, householdId);
        rabbitTemplate.send(EXCHANGE, PetEventMessage.PET_DELETED, json(body));
    }

    /**
     * Like a real producer's message: JSON plus Spring's {@code __TypeId__} header naming a class that does not
     * exist here. The consumer must ignore it (regression: it used to dead-letter every real pet event).
     */
    private static Message json(String body) {
        return MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8))
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .setHeader("__TypeId__", "com.pawtrack.pet.messaging.EventEnvelope")
            .build();
    }

    private String receiveFromDlq() {
        Message message = rabbitTemplate.receive(DLQ, TIMEOUT.toMillis());
        assertThat(message).as("message in " + DLQ).isNotNull();
        return new String(message.getBody(), StandardCharsets.UTF_8);
    }
}
