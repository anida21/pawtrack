package com.pawtrack.notification.messaging;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

import com.pawtrack.notification.entity.MedicationFrequency;

/**
 * {@code data} of every event this service consumes. Field names match {@code contracts/events.md};
 * unknown fields are ignored, so producers may add fields without breaking this service.
 */
public sealed interface ConsumedEvent {

    /**
     * Event type (routing key) → record it is read into. Other types are acknowledged and ignored.
     * {@code pet.updated} carries the same fields as {@code pet.created} and is applied the same way (upsert).
     */
    Map<String, Class<? extends ConsumedEvent>> TYPES = Map.ofEntries(
        Map.entry("household.member.added", HouseholdMemberAdded.class),
        Map.entry("household.member.removed", HouseholdMemberRemoved.class),
        Map.entry("household.deleted", HouseholdDeleted.class),
        Map.entry("household.invitation.created", HouseholdInvitationCreated.class),
        Map.entry("user.updated", UserUpdated.class),
        Map.entry("pet.created", PetCreated.class),
        Map.entry("pet.updated", PetCreated.class),
        Map.entry("pet.deleted", PetDeleted.class),
        Map.entry("health.vaccination.recorded", VaccinationRecorded.class),
        Map.entry("health.vaccination.deleted", VaccinationDeleted.class),
        Map.entry("health.medication.scheduled", MedicationScheduled.class),
        Map.entry("health.medication.cancelled", MedicationCancelled.class));

    record HouseholdMemberAdded(UUID householdId, UUID userId, String role) implements ConsumedEvent {
    }

    record HouseholdMemberRemoved(UUID householdId, UUID userId) implements ConsumedEvent {
    }

    record HouseholdDeleted(UUID householdId) implements ConsumedEvent {
    }

    record HouseholdInvitationCreated(UUID householdId, String householdName, String email, String code,
                                      UUID invitedBy, Instant expiresAt) implements ConsumedEvent {
    }

    record UserUpdated(UUID userId, String email, String displayName, String timezone,
                       Boolean emailNotifications) implements ConsumedEvent {
    }

    record PetCreated(UUID petId, UUID householdId, String name, String species) implements ConsumedEvent {
    }

    record PetDeleted(UUID petId, UUID householdId) implements ConsumedEvent {
    }

    record VaccinationRecorded(UUID vaccinationId, UUID petId, UUID householdId, String vaccine,
                               LocalDate administeredOn, LocalDate nextDueDate) implements ConsumedEvent {
    }

    record MedicationScheduled(UUID medicationId, UUID petId, UUID householdId, String name, String dosage,
                               LocalDate startDate, LocalDate endDate, MedicationFrequency frequency,
                               LocalTime timeOfDay) implements ConsumedEvent {
    }

    record VaccinationDeleted(UUID vaccinationId, UUID petId) implements ConsumedEvent {
    }

    record MedicationCancelled(UUID medicationId, UUID petId) implements ConsumedEvent {
    }
}
