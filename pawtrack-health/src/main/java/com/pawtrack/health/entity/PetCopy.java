package com.pawtrack.health.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Local copy of a pet ({@code petId -> householdId, name}, events.md), so events can name the household
 * without calling pet. Written only by the native upserts in {@code PetCopyRepository}.
 */
@Entity
@Table(name = "pets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PetCopy {

    @Id
    @Column(name = "pet_id")
    private UUID petId;

    @Column(name = "household_id", nullable = false)
    private UUID householdId;

    @Column(length = 60)
    private String name;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
