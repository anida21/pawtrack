package com.pawtrack.health.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.pawtrack.health.entity.Frequency;

/** {@code Medication} schema of health.yaml. */
public record MedicationResponse(
    UUID id,
    UUID petId,
    String name,
    String dosage,
    LocalDate startDate,
    LocalDate endDate,
    Frequency frequency,
    String timeOfDay,
    String notes,
    boolean active
) {
}
