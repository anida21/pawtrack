package com.pawtrack.health.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A vaccine given to a pet. Soft-deleted via {@code deletedAt}. */
@Entity
@Table(name = "vaccinations")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Vaccination implements HealthRecord {

    @Id
    @Setter(AccessLevel.NONE)
    private UUID id;

    @Setter(AccessLevel.NONE)
    @Column(name = "pet_id", nullable = false, updatable = false)
    private UUID petId;

    @Column(nullable = false, length = 100)
    private String vaccine;

    @Column(name = "administered_on", nullable = false)
    private LocalDate administeredOn;

    @Column(name = "next_due_date")
    private LocalDate nextDueDate;

    @Column(name = "batch_number", length = 50)
    private String batchNumber;

    @Column(length = 120)
    private String clinic;

    @Column(length = 2000)
    private String notes;

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

    public Vaccination(UUID petId, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.petId = petId;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }
}
