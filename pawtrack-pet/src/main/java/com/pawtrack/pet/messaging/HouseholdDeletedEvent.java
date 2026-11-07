package com.pawtrack.pet.messaging;

import java.time.Instant;
import java.util.UUID;

/** Incoming {@code household.deleted} envelope (events.md); unknown fields are ignored. */
public record HouseholdDeletedEvent(UUID eventId, String type, Instant occurredAt, String producer, Data data) {

    public static final String TYPE = "household.deleted";

    public record Data(UUID householdId) {
    }

    boolean isValid() {
        return eventId != null && TYPE.equals(type) && data != null && data.householdId() != null;
    }
}
