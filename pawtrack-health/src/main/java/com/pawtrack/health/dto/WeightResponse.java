package com.pawtrack.health.dto;

import java.time.LocalDate;
import java.util.UUID;

/** {@code Weight} schema of health.yaml. */
public record WeightResponse(UUID id, UUID petId, LocalDate measuredOn, Double weightKg) {
}
