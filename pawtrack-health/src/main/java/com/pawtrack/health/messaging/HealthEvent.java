package com.pawtrack.health.messaging;

import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pawtrack.health.entity.Frequency;
import com.pawtrack.health.entity.Medication;
import com.pawtrack.health.entity.Vaccination;

/**
 * Events published by this service. Each record is the {@code data} part of the envelope and must match
 * {@code pawtrack-development/contracts/events.md}. Optional fields ({@code ?} in events.md) are left out when empty.
 */
public sealed interface HealthEvent {

    /** Event type, also used as the routing key. */
    String type();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record VaccinationRecorded(UUID vaccinationId, UUID petId, UUID householdId, String vaccine,
                               LocalDate administeredOn, LocalDate nextDueDate) implements HealthEvent {

        public static VaccinationRecorded of(Vaccination vaccination, UUID householdId) {
            return new VaccinationRecorded(vaccination.getId(), vaccination.getPetId(), householdId,
                vaccination.getVaccine(), vaccination.getAdministeredOn(), vaccination.getNextDueDate());
        }

        @Override
        public String type() {
            return "health.vaccination.recorded";
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record MedicationScheduled(UUID medicationId, UUID petId, UUID householdId, String name, String dosage,
                               LocalDate startDate, LocalDate endDate, Frequency frequency,
                               String timeOfDay) implements HealthEvent {

        public static MedicationScheduled of(Medication medication, UUID householdId) {
            return new MedicationScheduled(medication.getId(), medication.getPetId(), householdId,
                medication.getName(), medication.getDosage(), medication.getStartDate(), medication.getEndDate(),
                medication.getFrequency(), medication.getTimeOfDay());
        }

        @Override
        public String type() {
            return "health.medication.scheduled";
        }
    }

    record MedicationCancelled(UUID medicationId, UUID petId) implements HealthEvent {

        public static MedicationCancelled of(Medication medication) {
            return new MedicationCancelled(medication.getId(), medication.getPetId());
        }

        @Override
        public String type() {
            return "health.medication.cancelled";
        }
    }
}
