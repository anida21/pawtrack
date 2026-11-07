package com.pawtrack.identity.messaging;

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
    IdentityEvent data
) {

    public static final String PRODUCER = "pawtrack-identity";

    public static EventEnvelope wrap(IdentityEvent event) {
        return new EventEnvelope(UUID.randomUUID(), event.type(), Instant.now(), PRODUCER, event);
    }
}
