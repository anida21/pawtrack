package com.pawtrack.identity.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record HouseholdResponse(
    UUID id,
    String name,
    List<MemberResponse> members,
    Instant createdAt
) {
}
