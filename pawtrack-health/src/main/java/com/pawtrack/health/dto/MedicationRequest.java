package com.pawtrack.health.dto;

import java.time.LocalDate;

import com.pawtrack.health.entity.Frequency;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body of create (POST) and full update (PUT). */
public record MedicationRequest(
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Size(max = 60) String dosage,
    @NotNull LocalDate startDate,
    LocalDate endDate,
    @NotNull Frequency frequency,
    @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$", message = "must be HH:mm") String timeOfDay,
    @Size(max = 2000) String notes
) {

    @AssertTrue(message = "endDate must not be before startDate")
    boolean isEndDateNotBeforeStartDate() {
        return endDate == null || startDate == null || !endDate.isBefore(startDate);
    }
}
