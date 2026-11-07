package com.pawtrack.notification.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import com.pawtrack.notification.config.PawtrackProperties;
import com.pawtrack.notification.entity.MedicationFrequency;
import com.pawtrack.notification.entity.MedicationSchedule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure date arithmetic of medication schedules (no Spring, no clock). */
class MedicationOccurrencesTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Sarajevo");

    private final MedicationReminderService service = new MedicationReminderService(null, null, null, null,
        new PawtrackProperties(null, new PawtrackProperties.Reminders("-", "-", 100, 30, 7,
            LocalTime.of(9, 0), LocalTime.of(8, 0), ZONE), null), null);

    @Test
    void monthly_keepsTheStartDayAndClampsToMonthEnd() {
        MedicationSchedule schedule = schedule(MedicationFrequency.MONTHLY, LocalDate.of(2027, 1, 31), null, null);

        List<Instant> result = service.occurrences(schedule, ZONE, at(2027, 1, 1, 0), at(2027, 5, 1, 0));

        assertThat(result).containsExactly(at(2027, 1, 31, 8), at(2027, 2, 28, 8), at(2027, 3, 31, 8),
            at(2027, 4, 30, 8));
    }

    @Test
    void rangeIsExclusiveAtTheStartAndInclusiveAtTheEnd() {
        MedicationSchedule schedule = schedule(MedicationFrequency.DAILY, LocalDate.of(2027, 1, 1), null,
            LocalTime.of(8, 0));

        assertThat(service.occurrences(schedule, ZONE, at(2027, 1, 1, 8), at(2027, 1, 3, 8)))
            .containsExactly(at(2027, 1, 2, 8), at(2027, 1, 3, 8));
    }

    @Test
    void twiceDaily_secondDoseIsTwelveHoursLater_evenAcrossMidnight() {
        MedicationSchedule schedule = schedule(MedicationFrequency.TWICE_DAILY, LocalDate.of(2027, 1, 1),
            LocalDate.of(2027, 1, 1), LocalTime.of(14, 0));

        assertThat(service.occurrences(schedule, ZONE, at(2026, 12, 31, 0), at(2027, 1, 10, 0)))
            .containsExactly(at(2027, 1, 1, 14), at(2027, 1, 2, 2));
    }

    @Test
    void daylightSavingChange_keepsLocalTime() {
        MedicationSchedule schedule = schedule(MedicationFrequency.DAILY, LocalDate.of(2027, 3, 27), null,
            LocalTime.of(8, 0));

        assertThat(service.occurrences(schedule, ZONE, at(2027, 3, 27, 0), at(2027, 3, 29, 0)))
            .containsExactly(at(2027, 3, 27, 8), at(2027, 3, 28, 8));
    }

    @Test
    void once_ignoresLaterDates() {
        MedicationSchedule schedule = schedule(MedicationFrequency.ONCE, LocalDate.of(2027, 1, 5), null,
            LocalTime.of(10, 0));

        assertThat(service.occurrences(schedule, ZONE, at(2027, 1, 1, 0), at(2027, 2, 1, 0)))
            .containsExactly(at(2027, 1, 5, 10));
    }

    private static Instant at(int year, int month, int day, int hour) {
        return ZonedDateTime.of(year, month, day, hour, 0, 0, 0, ZONE).toInstant();
    }

    private static MedicationSchedule schedule(MedicationFrequency frequency, LocalDate start, LocalDate end,
                                               LocalTime time) {
        MedicationSchedule schedule = new MedicationSchedule(UUID.randomUUID());
        schedule.reschedule(UUID.randomUUID(), UUID.randomUUID(), "Pill", "1", start, end, frequency, time,
            Instant.EPOCH);
        return schedule;
    }
}
