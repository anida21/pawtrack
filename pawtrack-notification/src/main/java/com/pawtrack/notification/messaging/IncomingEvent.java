package com.pawtrack.notification.messaging;

import java.time.Instant;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

/**
 * Envelope of a consumed event (see {@code contracts/events.md}). {@code data} stays raw JSON until
 * the type is known, then it is read into one of the {@link ConsumedEvent} records.
 */
public record IncomingEvent(
    UUID eventId,
    String type,
    Instant occurredAt,
    String producer,
    JsonNode data
) {
}
