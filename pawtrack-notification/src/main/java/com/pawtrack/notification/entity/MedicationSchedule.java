package com.pawtrack.notification.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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

/**
 * The latest {@code health.medication.scheduled} of one medication. Reminders exist up to
 * {@code generatedUntil}; a job moves that horizon forward (rolling, max 30 days ahead).
 */
@Entity
@Table(name = "medication_schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MedicationSchedule {

    @Id
    @Column(name = "medication_id")
    private UUID medicationId;

    @Column(name = "pet_id", nullable = false)
    private UUID petId;

    @Column(name = "household_id", nullable = false)
    private UUID householdId;

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
    private MedicationFrequency frequency;

    @Column(name = "time_of_day")
    private LocalTime timeOfDay;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "generated_until", nullable = false)
    private Instant generatedUntil;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public MedicationSchedule(UUID medicationId) {
        this.medicationId = medicationId;
    }

    /** Replaces the whole schedule; reminders must be regenerated from {@code now}. */
    public void reschedule(UUID petId, UUID householdId, String name, String dosage, LocalDate startDate,
                           LocalDate endDate, MedicationFrequency frequency, LocalTime timeOfDay, Instant now) {
        this.petId = petId;
        this.householdId = householdId;
        this.name = name;
        this.dosage = dosage;
        this.startDate = startDate;
        this.endDate = endDate;
        this.frequency = frequency;
        this.timeOfDay = timeOfDay;
        this.active = true;
        this.generatedUntil = now;
        this.updatedAt = now;
    }

    public void generatedUntil(Instant horizon) {
        this.generatedUntil = horizon;
    }

    public void deactivate(Instant now) {
        this.active = false;
        this.updatedAt = now;
    }
}
