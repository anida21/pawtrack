package com.pawtrack.health.dto;

import java.util.UUID;

/** The fields health needs from pet's {@code Pet} schema (pet.yaml); other fields are ignored. */
public record PetRef(UUID id, UUID householdId, String name) {
}
