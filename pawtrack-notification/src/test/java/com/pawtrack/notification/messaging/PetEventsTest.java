package com.pawtrack.notification.messaging;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.pawtrack.notification.entity.Pet;
import com.pawtrack.notification.entity.ReminderSource;
import com.pawtrack.notification.entity.ReminderStatus;
import com.pawtrack.notification.repository.MedicationScheduleRepository;
import com.pawtrack.notification.repository.PetRepository;
import com.pawtrack.notification.support.IntegrationTest;
import com.pawtrack.notification.support.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/** pet.created, pet.deleted. */
class PetEventsTest extends IntegrationTest {

    @Autowired
    PetRepository petRepository;

    @Autowired
    MedicationScheduleRepository scheduleRepository;

    @Test
    void petCreated_storesTheLocalCopy_andIsAnUpsert() {
        UUID householdId = UUID.randomUUID();
        UUID petId = petCreated(householdId, "Rex");
        UUID otherHousehold = UUID.randomUUID();
        publish("pet.created", data("petId", petId, "householdId", otherHousehold, "name", "Max"));

        Pet pet = petRepository.findById(petId).orElseThrow();
        assertThat(pet.getHouseholdId()).isEqualTo(otherHousehold);
        assertThat(pet.getName()).isEqualTo("Max");
        assertThat(pet.isDeleted()).isFalse();
    }

    @Test
    void petDeleted_cancelsPendingReminders_andStopsMedicationSchedules() throws Exception {
        TestUser owner = TestUser.random("Owner");
        TestHousehold home = household(owner);
        UUID custom = createReminder(owner, home.petId(), "Walk", Instant.now().plus(1, ChronoUnit.DAYS));
        UUID medicationId = UUID.randomUUID();
        publish("health.medication.scheduled", data("medicationId", medicationId, "petId", home.petId(),
            "householdId", home.id(), "name", "Pill", "dosage", "1", "startDate", LocalDate.now(ZoneOffset.UTC),
            "frequency", "DAILY"));
        assertThat(remindersOf(ReminderSource.MEDICATION, medicationId)).isNotEmpty();

        publish("pet.deleted", data("petId", home.petId(), "householdId", home.id()));

        assertThat(reminderRepository.findById(custom).orElseThrow().getStatus()).isEqualTo(ReminderStatus.CANCELLED);
        assertThat(remindersOf(ReminderSource.MEDICATION, medicationId))
            .allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(ReminderStatus.CANCELLED));
        assertThat(scheduleRepository.findById(medicationId).orElseThrow().isActive()).isFalse();
        assertThat(petRepository.findById(home.petId()).orElseThrow().isDeleted()).isTrue();
    }

    @Test
    void petDeleted_forUnknownPet_isRemembered() {
        UUID petId = UUID.randomUUID();
        UUID householdId = UUID.randomUUID();
        publish("pet.deleted", data("petId", petId, "householdId", householdId));

        // a late vaccination event for the deleted pet creates no reminders
        UUID vaccinationId = UUID.randomUUID();
        publish("health.vaccination.recorded", data("vaccinationId", vaccinationId, "petId", petId,
            "householdId", householdId, "vaccine", "Rabies", "administeredOn", LocalDate.now(ZoneOffset.UTC),
            "nextDueDate", LocalDate.now(ZoneOffset.UTC).plusYears(1)));

        assertThat(petRepository.findById(petId).orElseThrow().isDeleted()).isTrue();
        assertThat(remindersOf(ReminderSource.VACCINATION, vaccinationId)).isEmpty();
    }
}
