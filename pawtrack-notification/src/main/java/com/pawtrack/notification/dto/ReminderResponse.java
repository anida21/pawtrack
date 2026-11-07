package com.pawtrack.notification.dto;

import java.time.Instant;
import java.util.UUID;

import com.pawtrack.notification.entity.ReminderSource;
import com.pawtrack.notification.entity.ReminderStatus;

/** {@code Reminder} in notification.yaml. */
public record ReminderResponse(
    UUID id,
    UUID petId,
    UUID householdId,
    String title,
    String message,
    Instant dueAt,
    ReminderSource source,
    UUID sourceId,
    ReminderStatus status,
    Instant sentAt
) {
}
