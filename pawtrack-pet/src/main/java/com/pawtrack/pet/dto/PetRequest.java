package com.pawtrack.pet.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.pawtrack.pet.entity.Sex;
import com.pawtrack.pet.entity.Species;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/** Body of create (POST) and full update (PUT). */
public record PetRequest(
    @NotNull UUID householdId,
    @NotBlank @Size(max = 60) String name,
    @NotNull Species species,
    @Size(max = 80) String breed,
    Sex sex,
    @PastOrPresent LocalDate birthDate,
    Boolean neutered,
    @Pattern(regexp = "^[0-9]{15}$", message = "must be 15 digits") String microchipNumber,
    @Size(max = 60) String color,
    @Size(max = 2000) String notes
) {
}
