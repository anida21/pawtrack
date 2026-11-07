package com.pawtrack.health.service;

import java.time.Instant;
import java.util.UUID;

import com.pawtrack.health.entity.ProcessedEvent;
import com.pawtrack.health.messaging.PetEventMessage;
import com.pawtrack.health.repository.MedicationRepository;
import com.pawtrack.health.repository.PetCopyRepository;
import com.pawtrack.health.repository.ProcessedEventRepository;
import com.pawtrack.health.repository.VaccinationRepository;
import com.pawtrack.health.repository.VetVisitRepository;
import com.pawtrack.health.repository.WeightRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reacts to pet's events: keeps the local pet copy and soft-deletes the records of deleted pets.
 * Idempotent: an eventId that was already processed is skipped (marker stored in the same transaction).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PetCopyService {

    private final PetCopyRepository petCopies;
    private final ProcessedEventRepository processedEvents;
    private final VaccinationRepository vaccinations;
    private final VetVisitRepository vetVisits;
    private final MedicationRepository medications;
    private final WeightRepository weights;

    /**
     * Applies pet.created or pet.updated (also the backfill answer, events.md): insert or update the local copy.
     * A pet already known as deleted stays deleted.
     *
     * @return {@code false} for a redelivered event
     */
    @Transactional
    public boolean petUpserted(UUID eventId, String type, UUID petId, UUID householdId, String name) {
        if (!markProcessed(eventId, type)) {
            return false;
        }
        petCopies.upsert(petId, householdId, name, Instant.now());
        log.info("Pet {} ({}) in household {}", petId, type, householdId);
        return true;
    }

    /**
     * Marks the pet deleted and soft-deletes all its records. Pending medication reminders are cancelled by
     * notification from the same pet.deleted event (events.md), so no health.medication.cancelled is sent.
     *
     * @return {@code false} for a redelivered event
     */
    @Transactional
    public boolean petDeleted(UUID eventId, UUID petId, UUID householdId) {
        if (!markProcessed(eventId, PetEventMessage.PET_DELETED)) {
            return false;
        }
        Instant now = Instant.now();
        petCopies.markDeleted(petId, householdId, now);
        int records = vaccinations.softDeleteByPetId(petId, now)
            + vetVisits.softDeleteByPetId(petId, now)
            + medications.softDeleteByPetId(petId, now)
            + weights.softDeleteByPetId(petId, now);
        log.info("Pet {} deleted: soft-deleted {} health record(s)", petId, records);
        return true;
    }

    private boolean markProcessed(UUID eventId, String type) {
        if (processedEvents.existsById(eventId)) {
            log.info("Skipping already processed event {}", eventId);
            return false;
        }
        processedEvents.save(new ProcessedEvent(eventId, type));
        return true;
    }
}
