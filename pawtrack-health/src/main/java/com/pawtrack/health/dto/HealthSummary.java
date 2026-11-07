package com.pawtrack.health.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** {@code HealthSummary} schema of health.yaml (dashboard card). Fields without data are null. */
public record HealthSummary(
    UUID petId,
    Double latestWeightKg,
    Instant lastVetVisit,
    LocalDate nextVaccinationDue,
    int overdueVaccinations,
    int activeMedications
) {
}
