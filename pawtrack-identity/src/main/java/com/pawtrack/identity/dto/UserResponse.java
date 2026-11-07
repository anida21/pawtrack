package com.pawtrack.identity.dto;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String email,
    String displayName,
    String timezone,
    boolean emailNotifications,
    Instant createdAt
) {
}
