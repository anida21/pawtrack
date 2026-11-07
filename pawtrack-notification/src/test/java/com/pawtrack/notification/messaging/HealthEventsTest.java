package com.pawtrack.notification.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import com.pawtrack.notification.config.RabbitConfig;
import com.pawtrack.notification.entity.Reminder;
import com.pawtrack.notification.entity.ReminderSource;
import com.pawtrack.notification.entity.ReminderStatus;
import com.pawtrack.notification.repository.MedicationScheduleRepository;
import com.pawtrack.notification.service.MedicationReminderService;
import com.pawtrack.notification.support.IntegrationTest;
import com.pawtrack.notification.support.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** health.vaccination.recorded, health.medication.scheduled, health.medication.cancelled. */
class HealthEventsTest extends IntegrationTest {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    @Autowired
    MedicationScheduleRepository scheduleRepository;

    @Autowired
    MedicationReminderService medicationReminders;

    @Autowired
    JdbcTemplate jdbc;

    private TestUser owner;
    private TestHousehold home;
    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);

    @BeforeEach
    void household() {
        owner = TestUser.random("Owner");
        home = household(owner);
    }

    // ---- vaccinations --------------------------------------------------------------------------------------

    @Test
    void vaccinationRecorded_createsRemindersSevenDaysBeforeAndOnTheDay_at0900OwnerTime() {
        TestUser member = TestUser.random("Member");
        userUpdated(member, "Asia/Tokyo", true);
        memberAdded(home.id(), member, "MEMBER");
        userUpdated(owner, "America/New_York", true);
        LocalDate due = today.plusDays(30);
        UUID vaccinationId = recordVaccination(due);

        List<Reminder> reminders = remindersOf(ReminderSource.VACCINATION, vaccinationId);
        assertThat(reminders).extracting(Reminder::getDueAt).containsExactly(
            ZonedDateTime.of(due.minusDays(7), LocalTime.of(9, 0), NEW_YORK).toInstant(),
            ZonedDateTime.of(due, LocalTime.of(9, 0), NEW_YORK).toInstant());
        assertThat(reminders).allSatisfy(r -> {
            assertThat(r.getStatus()).isEqualTo(ReminderStatus.PENDING);
            assertThat(r.getPetId()).isEqualTo(home.petId());
            assertThat(r.getHouseholdId()).isEqualTo(home.id());
            assertThat(r.getMessage()).isEqualTo("Rex's Rabies vaccination is due on " + due + ".");
        });
        assertThat(reminders).extracting(Reminder::getTitle)
            .containsExactly("Rex: Rabies vaccination due in 7 days", "Rex: Rabies vaccination due today");
    }

    @Test
    void vaccinationRecorded_withoutKnownTimezone_usesDefault() {
        UUID householdId = UUID.randomUUID();
        UUID petId = petCreated(householdId, "Mia");
        LocalDate due = today.plusDays(20);
        UUID vaccinationId = UUID.randomUUID();
        publish("health.vaccination.recorded", data("vaccinationId", vaccinationId, "petId", petId,
            "householdId", householdId, "vaccine", "Rabies", "administeredOn", today, "nextDueDate", due));

        assertThat(remindersOf(ReminderSource.VACCINATION, vaccinationId)).extracting(Reminder::getDueAt)
            .contains(ZonedDateTime.of(due, LocalTime.of(9, 0), ZoneId.of("Europe/Sarajevo")).toInstant());
    }

    @Test
    void vaccinationRecorded_dueWithinSevenDays_onlyCreatesTheOnTheDayReminder() {
        UUID vaccinationId = recordVaccination(today.plusDays(3));

        assertThat(remindersOf(ReminderSource.VACCINATION, vaccinationId)).extracting(Reminder::getDueAt)
            .containsExactly(ZonedDateTime.of(today.plusDays(3), LocalTime.of(9, 0), ZoneOffset.UTC).toInstant());
    }

    @Test
    void vaccinationRecordedAgain_replacesPendingReminders() {
        UUID vaccinationId = recordVaccination(today.plusDays(30));
        recordVaccination(vaccinationId, today.plusDays(60));

        List<Reminder> reminders = remindersOf(ReminderSource.VACCINATION, vaccinationId);
        assertThat(reminders).hasSize(4);
        assertThat(reminders.subList(0, 2)).allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(ReminderStatus.CANCELLED));
        assertThat(reminders.subList(2, 4)).allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(ReminderStatus.PENDING));
        assertThat(reminders.get(3).getDueAt())
            .isEqualTo(ZonedDateTime.of(today.plusDays(60), LocalTime.of(9, 0), ZoneOffset.UTC).toInstant());
    }

    @Test
    void vaccinationRecordedWithoutNextDueDate_cancelsPendingReminders() {
        UUID vaccinationId = recordVaccination(today.plusDays(30));
        recordVaccination(vaccinationId, null);

        assertThat(remindersOf(ReminderSource.VACCINATION, vaccinationId)).hasSize(2)
            .allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(ReminderStatus.CANCELLED));
    }

    // ---- medications ---------------------------------------------------------------------------------------

    @Test
    void medicationScheduled_daily_createsRemindersForThirtyDaysAtTimeOfDay() {
        UUID medicationId = scheduleMedication(UUID.randomUUID(), "DAILY", today, null, "08:00");
        Instant now = Instant.now();

        List<Reminder> reminders = remindersOf(ReminderSource.MEDICATION, medicationId);
        assertThat(reminders).hasSizeBetween(30, 31);
        assertThat(reminders).allSatisfy(r -> {
            assertThat(r.getDueAt()).isAfter(now.minusSeconds(60)).isBefore(now.plus(Duration.ofDays(30)));
            assertThat(r.getDueAt().atZone(ZoneOffset.UTC).toLocalTime()).isEqualTo(LocalTime.of(8, 0));
            assertThat(r.getTitle()).isEqualTo("Rex: give Pill");
            assertThat(r.getMessage()).isEqualTo("Time to give Rex Pill (1 tablet).");
        });
        assertThat(scheduleRepository.findById(medicationId).orElseThrow().isActive()).isTrue();
        medicationReminders.topUpAll();
        assertThat(remindersOf(ReminderSource.MEDICATION, medicationId)).as("no duplicates on top-up")
            .hasSize(reminders.size());
    }

    @Test
    void medicationScheduled_twiceDailyWithEndDate_stopsAtEndDate() {
        UUID medicationId = scheduleMedication(UUID.randomUUID(), "TWICE_DAILY", today, today.plusDays(2), "08:00");

        List<Reminder> reminders = remindersOf(ReminderSource.MEDICATION, medicationId);
        assertThat(reminders).hasSizeBetween(4, 6);
        assertThat(reminders).extracting(r -> r.getDueAt().atZone(ZoneOffset.UTC).toLocalTime())
            .containsOnly(LocalTime.of(8, 0), LocalTime.of(20, 0));
        assertThat(reminders.get(reminders.size() - 1).getDueAt())
            .isEqualTo(ZonedDateTime.of(today.plusDays(2), LocalTime.of(20, 0), ZoneOffset.UTC).toInstant());
        assertThat(scheduleRepository.findById(medicationId).orElseThrow().isActive())
            .as("all occurrences generated").isFalse();
    }

    @Test
    void medicationScheduled_weeklyWithoutTimeOfDay_usesDefaultTimeAndStartDateRhythm() {
        LocalDate start = today.minusDays(10);
        UUID medicationId = scheduleMedication(UUID.randomUUID(), "WEEKLY", start, null, null);

        List<Reminder> reminders = remindersOf(ReminderSource.MEDICATION, medicationId);
        assertThat(reminders).hasSizeBetween(4, 5).allSatisfy(r -> {
            ZonedDateTime at = r.getDueAt().atZone(ZoneOffset.UTC);
            assertThat(at.toLocalTime()).isEqualTo(LocalTime.of(8, 0));
            assertThat(java.time.temporal.ChronoUnit.DAYS.between(start, at.toLocalDate()) % 7).isZero();
        });
    }

    @Test
    void medicationScheduled_once_createsOneReminder() {
        UUID medicationId = scheduleMedication(UUID.randomUUID(), "ONCE", today.plusDays(1), null, "10:30");

        assertThat(remindersOf(ReminderSource.MEDICATION, medicationId)).extracting(Reminder::getDueAt)
            .containsExactly(ZonedDateTime.of(today.plusDays(1), LocalTime.of(10, 30), ZoneOffset.UTC).toInstant());
    }

    @Test
    void medicationScheduledAgain_replacesFutureReminders() {
        UUID medicationId = scheduleMedication(UUID.randomUUID(), "DAILY", today, null, "08:00");
        scheduleMedication(medicationId, "DAILY", today, null, "18:00");

        List<Reminder> reminders = remindersOf(ReminderSource.MEDICATION, medicationId);
        assertThat(reminders.stream().filter(r -> r.getStatus() == ReminderStatus.PENDING))
            .hasSizeBetween(30, 31)
            .allSatisfy(r -> assertThat(r.getDueAt().atZone(ZoneOffset.UTC).toLocalTime()).isEqualTo(LocalTime.of(18, 0)));
        assertThat(reminders.stream().filter(r -> r.getDueAt().atZone(ZoneOffset.UTC).getHour() == 8))
            .allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(ReminderStatus.CANCELLED));
    }

    @Test
    void medicationCancelled_cancelsPendingReminders_andStopsTheSchedule() {
        UUID medicationId = scheduleMedication(UUID.randomUUID(), "DAILY", today, null, "08:00");
        publish("health.medication.cancelled", data("medicationId", medicationId, "petId", home.petId()));

        assertThat(remindersOf(ReminderSource.MEDICATION, medicationId)).isNotEmpty()
            .allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(ReminderStatus.CANCELLED));
        assertThat(scheduleRepository.findById(medicationId).orElseThrow().isActive()).isFalse();
        medicationReminders.topUpAll();
        assertThat(remindersOf(ReminderSource.MEDICATION, medicationId))
            .allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(ReminderStatus.CANCELLED));
    }

    @Test
    void topUp_extendsTheRollingHorizon() {
        UUID medicationId = scheduleMedication(UUID.randomUUID(), "DAILY", today, null, "08:00");
        int before = remindersOf(ReminderSource.MEDICATION, medicationId).size();
        // pretend the schedule was generated 10 days ago: only 20 days are covered
        jdbc.update("delete from reminders where source_id = ? and due_at > now() + interval '20 days'", medicationId);
        jdbc.update("update medication_schedules set generated_until = now() + interval '20 days' "
            + "where medication_id = ?", medicationId);
        assertThat(remindersOf(ReminderSource.MEDICATION, medicationId)).hasSizeLessThan(before);

        medicationReminders.topUpAll();

        assertThat(remindersOf(ReminderSource.MEDICATION, medicationId)).hasSize(before);
    }

    @Test
    void invalidEvent_isDeadLettered() {
        UUID medicationId = UUID.randomUUID();
        send("health.medication.scheduled", data("medicationId", medicationId, "petId", home.petId(),
            "householdId", home.id(), "name", "Pill", "dosage", "1", "startDate", today, "frequency", "HOURLY"));

        Message dead = rabbitTemplate.receive(RabbitConfig.HEALTH_QUEUE + ".dlq", EVENT_TIMEOUT.toMillis());
        assertThat(dead).isNotNull();
        assertThat(new String(dead.getBody(), StandardCharsets.UTF_8)).contains(medicationId.toString());
        assertThat(scheduleRepository.findById(medicationId)).isEmpty();
    }

    // ---- helpers -------------------------------------------------------------------------------------------

    private UUID recordVaccination(LocalDate nextDueDate) {
        return recordVaccination(UUID.randomUUID(), nextDueDate);
    }

    private UUID recordVaccination(UUID vaccinationId, LocalDate nextDueDate) {
        publish("health.vaccination.recorded", data("vaccinationId", vaccinationId, "petId", home.petId(),
            "householdId", home.id(), "vaccine", "Rabies", "administeredOn", today, "nextDueDate", nextDueDate));
        return vaccinationId;
    }

    private UUID scheduleMedication(UUID medicationId, String frequency, LocalDate start, LocalDate end,
                                    String timeOfDay) {
        publish("health.medication.scheduled", data("medicationId", medicationId, "petId", home.petId(),
            "householdId", home.id(), "name", "Pill", "dosage", "1 tablet", "startDate", start, "endDate", end,
            "frequency", frequency, "timeOfDay", timeOfDay));
        return medicationId;
    }
}
