package com.pawtrack.identity.dto;

import java.time.Instant;

import com.pawtrack.identity.entity.HouseholdRole;

public record InvitationResponse(
    String code,
    String email,
    HouseholdRole role,
    Instant expiresAt
) {
}
