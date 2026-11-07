package com.pawtrack.health.entity;

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

/** A medication plan of a pet. Soft-deleted via {@code deletedAt}. */
@Entity
@Table(name = "medications")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Medication implements HealthRecord {

    @Id
    @Setter(AccessLevel.NONE)
    private UUID id;

    @Setter(AccessLevel.NONE)
    @Column(name = "pet_id", nullable = false, updatable = false)
    private UUID petId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 60)
    private String dosage;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Frequency frequency;

    @Column(name = "time_of_day", length = 5)
    private String timeOfDay;

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

    public Medication(UUID petId, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.petId = petId;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    /**
     * Not finished yet on {@code today} (health.yaml): the last day is {@code endDate}, or {@code startDate}
     * for a ONCE medication without one; otherwise there is no last day.
     */
    public boolean isActiveOn(LocalDate today) {
        LocalDate lastDay = endDate != null ? endDate : frequency == Frequency.ONCE ? startDate : null;
        return lastDay == null || !lastDay.isBefore(today);
    }
}
