package com.pawtrack.health.dto;

import java.time.Instant;
import java.util.UUID;

/** {@code VetVisit} schema of health.yaml. */
public record VetVisitResponse(
    UUID id,
    UUID petId,
    Instant visitDate,
    String clinic,
    String vetName,
    String reason,
    String diagnosis,
    String treatment,
    Double cost,
    String currency,
    Instant createdAt
) {
}
