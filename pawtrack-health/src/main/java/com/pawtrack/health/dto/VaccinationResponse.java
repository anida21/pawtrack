package com.pawtrack.health.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** {@code Vaccination} schema of health.yaml. */
public record VaccinationResponse(
    UUID id,
    UUID petId,
    String vaccine,
    LocalDate administeredOn,
    LocalDate nextDueDate,
    String batchNumber,
    String clinic,
    String notes,
    Instant createdAt
) {
}
