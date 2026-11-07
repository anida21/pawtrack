package com.pawtrack.pet.service;

import com.pawtrack.pet.messaging.EventEnvelope;
import com.pawtrack.pet.messaging.PetEvent.PetUpdated;
import com.pawtrack.pet.messaging.RabbitEventRelay;
import com.pawtrack.pet.repository.PetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Republishes every pet that is not deleted as {@code pet.updated} (backfill, events.md). Nothing changes here,
 * so events are sent right away instead of after a commit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplayService {

    private final PetRepository pets;
    private final RabbitEventRelay relay;

    @Transactional(readOnly = true)
    public void replayPets() {
        var all = pets.findByDeletedAtIsNull();
        all.forEach(pet -> relay.relay(EventEnvelope.wrap(PetUpdated.of(pet))));
        log.info("Replayed {} pets", all.size());
    }
}
