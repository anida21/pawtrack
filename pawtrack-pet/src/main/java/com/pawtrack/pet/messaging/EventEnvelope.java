package com.pawtrack.pet.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire format shared by all PawTrack events (see {@code contracts/events.md}).
 */
public record EventEnvelope(
    UUID eventId,
    String type,
    Instant occurredAt,
    String producer,
    PetEvent data
) {

    public static final String PRODUCER = "pawtrack-pet";

    public static EventEnvelope wrap(PetEvent event) {
        return new EventEnvelope(UUID.randomUUID(), event.type(), Instant.now(), PRODUCER, event);
    }
}
