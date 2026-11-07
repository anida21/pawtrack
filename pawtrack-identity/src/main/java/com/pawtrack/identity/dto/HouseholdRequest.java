package com.pawtrack.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HouseholdRequest(
    @NotBlank @Size(max = 100) String name
) {
}
