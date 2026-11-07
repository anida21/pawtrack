package com.pawtrack.pet.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A pet of a household. Rows are never removed: {@code deletedAt} marks a soft delete,
 * and soft-deleted pets behave as if they did not exist.
 */
@Entity
@Table(name = "pets")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pet {

    @Id
    private UUID id;

    @Column(name = "household_id", nullable = false)
    private UUID householdId;

    @Column(nullable = false, length = 60)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Species species;

    @Column(length = 80)
    private String breed;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Sex sex;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    private Boolean neutered;

    @Column(name = "microchip_number", length = 15)
    private String microchipNumber;

    @Column(length = 60)
    private String color;

    @Column(length = 2000)
    private String notes;

    @Column(name = "photo_key", length = 200)
    private String photoKey;

    @Column(name = "photo_content_type", length = 40)
    private String photoContentType;

    @Setter(AccessLevel.NONE)
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Setter(AccessLevel.NONE)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Pet(UUID createdBy) {
        this.id = UUID.randomUUID();
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean hasPhoto() {
        return photoKey != null;
    }

    public void markDeleted() {
        if (deletedAt == null) {
            deletedAt = Instant.now();
        }
    }

    public void touch() {
        updatedAt = Instant.now();
    }
}
