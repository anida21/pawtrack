package com.pawtrack.identity.dto;

import java.util.UUID;

import com.pawtrack.identity.entity.HouseholdRole;

public record HouseholdMembershipResponse(
    UUID householdId,
    String householdName,
    HouseholdRole role
) {
}
