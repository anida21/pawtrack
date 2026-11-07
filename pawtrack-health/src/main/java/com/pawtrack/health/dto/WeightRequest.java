package com.pawtrack.health.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

public record WeightRequest(
    @NotNull @PastOrPresent LocalDate measuredOn,
    @NotNull @DecimalMin("0.01") @DecimalMax("200") Double weightKg
) {
}
