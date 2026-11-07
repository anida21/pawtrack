package com.pawtrack.pet.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.pawtrack.pet.entity.Sex;
import com.pawtrack.pet.entity.Species;

/** {@code Pet} schema of pet.yaml. */
public record PetResponse(
    UUID id,
    UUID householdId,
    String name,
    Species species,
    String breed,
    Sex sex,
    LocalDate birthDate,
    Boolean neutered,
    String microchipNumber,
    String color,
    String notes,
    Integer ageInMonths,
    boolean hasPhoto,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt
) {
}
