package com.pawtrack.health.messaging;

import java.time.Instant;
import java.util.UUID;

/** Incoming {@code pet.created} / {@code pet.updated} / {@code pet.deleted} envelope (events.md); unknown fields are ignored. */
public record PetEventMessage(UUID eventId, String type, Instant occurredAt, String producer, Data data) {

    public static final String PET_CREATED = "pet.created";
    public static final String PET_UPDATED = "pet.updated";
    public static final String PET_DELETED = "pet.deleted";

    /** Union of the events' data: {@code name} only comes with pet.created / pet.updated. */
    public record Data(UUID petId, UUID householdId, String name) {
    }

    /** pet.created and pet.updated are both applied as an upsert of the local copy. */
    boolean isUpsert() {
        return PET_CREATED.equals(type) || PET_UPDATED.equals(type);
    }

    boolean isValid() {
        if (eventId == null || data == null || data.petId() == null || data.householdId() == null) {
            return false;
        }
        return PET_DELETED.equals(type)
            || isUpsert() && data.name() != null && !data.name().isBlank();
    }
}
