package com.pawtrack.health.service;

import java.time.Instant;
import java.util.UUID;

import com.pawtrack.health.config.PetClient;
import com.pawtrack.health.dto.PetRef;
import com.pawtrack.health.entity.PetCopy;
import com.pawtrack.health.exception.NotFoundException;
import com.pawtrack.health.repository.PetCopyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Access rule of health.yaml: the caller may access a pet's records if pet answers
 * {@code GET /api/v1/pets/{petId}} with 200 for the caller's token. Read and write use the same rule.
 * A pet already known here as deleted (pet.deleted) is a 404 without asking pet.
 */
@Component
@RequiredArgsConstructor
public class PetAccess {

    private final PetClient petClient;
    private final PetCopyRepository petCopies;

    public PetRef check(Jwt jwt, UUID petId) {
        if (petCopies.findById(petId).map(PetCopy::isDeleted).orElse(false)) {
            throw new NotFoundException("Pet not found");
        }
        return petClient.getPet(jwt, petId);
    }

    /**
     * {@link #check} plus a refresh of the local pet copy from pet's answer (events.md has no pet.updated,
     * so this keeps the household and name current). Must run inside a write transaction.
     */
    public PetRef checkForWrite(Jwt jwt, UUID petId) {
        PetRef pet = check(jwt, petId);
        petCopies.upsert(pet.id(), pet.householdId(), pet.name(), Instant.now());
        return pet;
    }
}
