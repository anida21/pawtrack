package com.pawtrack.notification.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Custom reminder (source=CUSTOM). */
public record ReminderRequest(
    @NotNull UUID petId,
    @NotBlank @Size(max = 120) String title,
    @Size(max = 1000) String message,
    @NotNull Instant dueAt
) {
}
