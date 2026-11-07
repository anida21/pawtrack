package com.pawtrack.health.entity;

import java.time.Instant;
import java.util.UUID;

/** A record that can be edited and deleted through the API (vaccination, vet visit, medication). */
public interface HealthRecord {

    UUID getPetId();

    void setUpdatedAt(Instant updatedAt);

    void setDeletedAt(Instant deletedAt);

    default void touch() {
        setUpdatedAt(Instant.now());
    }

    default void markDeleted() {
        setDeletedAt(Instant.now());
    }
}
