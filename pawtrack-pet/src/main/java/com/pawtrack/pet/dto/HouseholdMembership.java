package com.pawtrack.pet.dto;

import java.util.UUID;

/** One entry of identity's {@code GET /api/v1/me/households} (identity.yaml {@code HouseholdMembership}). */
public record HouseholdMembership(UUID householdId, String householdName, HouseholdRole role) {
}
