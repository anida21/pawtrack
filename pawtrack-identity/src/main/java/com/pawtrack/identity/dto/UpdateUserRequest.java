package com.pawtrack.identity.dto;

import jakarta.validation.constraints.Size;

/** Partial update: {@code null} fields are left unchanged. */
public record UpdateUserRequest(
    @Size(min = 1, max = 100) String displayName,
    @TimeZoneId String timezone,
    Boolean emailNotifications
) {
}
