package com.pawtrack.health.entity;

import java.math.BigDecimal;
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

/** One weight measurement. Soft-deleted only together with its pet (the API has no weight DELETE). */
@Entity
@Table(name = "weights")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Weight {

    @Id
    @Setter(AccessLevel.NONE)
    private UUID id;

    @Setter(AccessLevel.NONE)
    @Column(name = "pet_id", nullable = false, updatable = false)
    private UUID petId;

    @Column(name = "measured_on", nullable = false)
    private LocalDate measuredOn;

    @Column(name = "weight_kg", nullable = false, precision = 6, scale = 3)
    private BigDecimal weightKg;

    @Setter(AccessLevel.NONE)
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Setter(AccessLevel.NONE)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Weight(UUID petId, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.petId = petId;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }
}
