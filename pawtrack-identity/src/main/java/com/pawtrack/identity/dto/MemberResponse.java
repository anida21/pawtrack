package com.pawtrack.identity.dto;

import java.time.Instant;
import java.util.UUID;

import com.pawtrack.identity.entity.HouseholdRole;

public record MemberResponse(
    UUID userId,
    String displayName,
    String email,
    HouseholdRole role,
    Instant joinedAt
) {
}
