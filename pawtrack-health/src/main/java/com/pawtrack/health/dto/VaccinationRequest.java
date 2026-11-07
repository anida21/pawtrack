package com.pawtrack.health.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/** Body of create (POST) and full update (PUT). */
public record VaccinationRequest(
    @NotBlank @Size(max = 100) String vaccine,
    @NotNull @PastOrPresent LocalDate administeredOn,
    LocalDate nextDueDate,
    @Size(max = 50) String batchNumber,
    @Size(max = 120) String clinic,
    @Size(max = 2000) String notes
) {

    @AssertTrue(message = "nextDueDate must not be before administeredOn")
    boolean isNextDueDateNotBeforeAdministeredOn() {
        return nextDueDate == null || administeredOn == null || !nextDueDate.isBefore(administeredOn);
    }
}
