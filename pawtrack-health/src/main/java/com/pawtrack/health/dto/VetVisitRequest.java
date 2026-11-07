package com.pawtrack.health.dto;

import java.time.Instant;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body of create (POST) and full update (PUT). {@code currency} defaults to BAM. */
public record VetVisitRequest(
    @NotNull Instant visitDate,
    @Size(max = 120) String clinic,
    @Size(max = 100) String vetName,
    @NotBlank @Size(max = 200) String reason,
    @Size(max = 2000) String diagnosis,
    @Size(max = 2000) String treatment,
    @DecimalMin("0") @DecimalMax("1000000000") Double cost,
    @Pattern(regexp = "^[A-Z]{3}$", message = "must be a 3-letter ISO currency code") String currency
) {
}
