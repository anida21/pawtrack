package com.pawtrack.health.messaging;

import java.time.Instant;
import java.util.UUID;

/** Incoming {@code pet.created} / {@code pet.deleted} envelope (events.md); unknown fields are ignored. */
public record PetEventMessage(UUID eventId, String type, Instant occurredAt, String producer, Data data) {

    public static final String PET_CREATED = "pet.created";
    public static final String PET_DELETED = "pet.deleted";

    /** Union of both events' data: {@code name} only comes with pet.created. */
    public record Data(UUID petId, UUID householdId, String name) {
    }

    boolean isValid() {
        if (eventId == null || data == null || data.petId() == null || data.householdId() == null) {
            return false;
        }
        return PET_DELETED.equals(type)
            || PET_CREATED.equals(type) && data.name() != null && !data.name().isBlank();
    }
}
