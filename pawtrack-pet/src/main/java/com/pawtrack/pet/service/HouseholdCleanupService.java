package com.pawtrack.pet.service;

import java.util.List;
import java.util.UUID;

import com.pawtrack.pet.entity.Pet;
import com.pawtrack.pet.entity.ProcessedEvent;
import com.pawtrack.pet.messaging.EventPublisher;
import com.pawtrack.pet.messaging.HouseholdDeletedEvent;
import com.pawtrack.pet.messaging.PetEvent;
import com.pawtrack.pet.repository.PetRepository;
import com.pawtrack.pet.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reacts to identity's household events. */
@Slf4j
@Service
@RequiredArgsConstructor
public class HouseholdCleanupService {

    private final PetRepository petRepository;
    private final ProcessedEventRepository processedEvents;
    private final EventPublisher eventPublisher;

    /**
     * Soft-deletes every active pet of the household and publishes {@code pet.deleted} for each,
     * so health and notification clean up as if the pets had been deleted one by one.
     * Idempotent: an eventId that was already processed is skipped.
     *
     * @return number of pets deleted (0 for a redelivered event)
     */
    @Transactional
    public int householdDeleted(UUID eventId, UUID householdId) {
        if (processedEvents.existsById(eventId)) {
            log.info("Skipping already processed event {}", eventId);
            return 0;
        }
        processedEvents.save(new ProcessedEvent(eventId, HouseholdDeletedEvent.TYPE));

        List<Pet> pets = petRepository.findByHouseholdIdAndDeletedAtIsNull(householdId);
        for (Pet pet : pets) {
            pet.markDeleted();
            eventPublisher.publish(PetEvent.PetDeleted.of(pet));
        }
        log.info("Household {} deleted: soft-deleted {} pet(s)", householdId, pets.size());
        return pets.size();
    }
}
