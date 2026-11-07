package com.pawtrack.health.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A visit to the vet (past or planned). Soft-deleted via {@code deletedAt}. */
@Entity
@Table(name = "vet_visits")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VetVisit implements HealthRecord {

    @Id
    @Setter(AccessLevel.NONE)
    private UUID id;

    @Setter(AccessLevel.NONE)
    @Column(name = "pet_id", nullable = false, updatable = false)
    private UUID petId;

    @Column(name = "visit_date", nullable = false)
    private Instant visitDate;

    @Column(length = 120)
    private String clinic;

    @Column(name = "vet_name", length = 100)
    private String vetName;

    @Column(nullable = false, length = 200)
    private String reason;

    @Column(length = 2000)
    private String diagnosis;

    @Column(length = 2000)
    private String treatment;

    @Column(precision = 12, scale = 2)
    private BigDecimal cost;

    @Column(nullable = false, length = 3)
    private String currency;

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

    public VetVisit(UUID petId, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.petId = petId;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }
}
