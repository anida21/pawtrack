package com.pawtrack.notification.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import com.pawtrack.notification.config.PawtrackProperties;
import com.pawtrack.notification.entity.MedicationFrequency;
import com.pawtrack.notification.entity.MedicationSchedule;
import com.pawtrack.notification.entity.Reminder;
import com.pawtrack.notification.entity.ReminderSource;
import com.pawtrack.notification.messaging.ConsumedEvent.MedicationCancelled;
import com.pawtrack.notification.messaging.ConsumedEvent.MedicationScheduled;
import com.pawtrack.notification.repository.MedicationScheduleRepository;
import com.pawtrack.notification.repository.ReminderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Medication reminders are generated from the stored schedule, at most {@code medication-horizon-days} ahead.
 * A job ({@link ReminderJobs}) moves the horizon forward, so long-running medications stay covered (rolling).
 *
 * <ul>
 *   <li>Occurrences: ONCE = startDate; DAILY = every day; TWICE_DAILY = every day at timeOfDay and 12 h later;
 *       WEEKLY = every 7 days from startDate; MONTHLY = same day-of-month as startDate (clamped to month end).
 *       None after endDate.</li>
 *   <li>Time: timeOfDay (default {@code default-medication-time}) in the household's time zone.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MedicationReminderService {

    private final MedicationScheduleRepository scheduleRepository;
    private final ReminderRepository reminderRepository;
    private final HouseholdTimeZones timeZones;
    private final PetNames petNames;
    private final PawtrackProperties properties;
    private final Clock clock;

    /** {@code health.medication.scheduled}: replace this medication's future reminders. */
    @Transactional
    public void onScheduled(MedicationScheduled event) {
        Instant now = clock.instant();
        reminderRepository.cancelPendingBySourceDueAfter(ReminderSource.MEDICATION, event.medicationId(), now, now);
        MedicationSchedule schedule = scheduleRepository.findById(event.medicationId())
            .orElseGet(() -> new MedicationSchedule(event.medicationId()));
        schedule.reschedule(event.petId(), event.householdId(), event.name(), event.dosage(), event.startDate(),
            event.endDate(), event.frequency(), event.timeOfDay(), now);
        if (petNames.isDeleted(event.petId())) {
            schedule.deactivate(now);
        }
        // assigned id → save() merges: keep working on the managed copy it returns
        topUp(scheduleRepository.save(schedule), now);
    }

    /** {@code health.medication.cancelled}: cancel all pending reminders and stop generating new ones. */
    @Transactional
    public void onCancelled(MedicationCancelled event) {
        Instant now = clock.instant();
        reminderRepository.cancelPendingBySource(ReminderSource.MEDICATION, event.medicationId(), now);
        scheduleRepository.findById(event.medicationId()).ifPresent(schedule -> schedule.deactivate(now));
    }

    /** Extends every active schedule up to now + horizon. */
    @Transactional
    public int topUpAll() {
        Instant now = clock.instant();
        int created = 0;
        for (MedicationSchedule schedule : scheduleRepository.findByActiveTrue()) {
            created += topUp(schedule, now);
        }
        return created;
    }

    /** Stops generating reminders for these schedules (pet or household deleted). */
    void deactivate(List<MedicationSchedule> schedules, Instant now) {
        schedules.forEach(schedule -> schedule.deactivate(now));
    }

    private int topUp(MedicationSchedule schedule, Instant now) {
        if (!schedule.isActive()) {
            return 0;
        }
        Instant horizon = now.plus(Duration.ofDays(properties.reminders().medicationHorizonDays()));
        Instant from = schedule.getGeneratedUntil().isAfter(now) ? schedule.getGeneratedUntil() : now;
        if (!from.isBefore(horizon)) {
            return 0;
        }
        ZoneId zone = timeZones.zoneOf(schedule.getHouseholdId());
        String pet = petNames.nameOf(schedule.getPetId());
        String title = "%s: give %s".formatted(pet, schedule.getName());
        String message = "Time to give %s %s (%s).".formatted(pet, schedule.getName(), schedule.getDosage());

        List<Reminder> reminders = occurrences(schedule, zone, from, horizon).stream()
            .map(dueAt -> new Reminder(schedule.getPetId(), schedule.getHouseholdId(), title, message, dueAt,
                ReminderSource.MEDICATION, schedule.getMedicationId(), null))
            .toList();
        reminderRepository.saveAll(reminders);
        schedule.generatedUntil(horizon);
        if (lastOccurrenceBefore(schedule, zone, horizon)) {
            schedule.deactivate(now);
        }
        return reminders.size();
    }

    /** Occurrences in ({@code fromExclusive}, {@code toInclusive}], ascending. */
    List<Instant> occurrences(MedicationSchedule schedule, ZoneId zone, Instant fromExclusive, Instant toInclusive) {
        LocalTime time = timeOf(schedule);
        LocalDate lastDate = toInclusive.atZone(zone).toLocalDate();
        List<Instant> result = new ArrayList<>();
        for (long n = 0; ; n++) {
            LocalDate date = nthDate(schedule, n);
            if (date == null || date.isAfter(lastDate)
                || (schedule.getEndDate() != null && date.isAfter(schedule.getEndDate()))) {
                break;
            }
            ZonedDateTime first = ZonedDateTime.of(date, time, zone);
            List<ZonedDateTime> times = schedule.getFrequency() == MedicationFrequency.TWICE_DAILY
                ? List.of(first, first.plusHours(12)) : List.of(first);
            for (ZonedDateTime at : times) {
                Instant instant = at.toInstant();
                if (instant.isAfter(fromExclusive) && !instant.isAfter(toInclusive)) {
                    result.add(instant);
                }
            }
        }
        return result;
    }

    private boolean lastOccurrenceBefore(MedicationSchedule schedule, ZoneId zone, Instant horizon) {
        LocalDate last = schedule.getFrequency() == MedicationFrequency.ONCE
            ? schedule.getStartDate() : schedule.getEndDate();
        // +1 day covers TWICE_DAILY's second dose after midnight
        return last != null && !ZonedDateTime.of(last.plusDays(1), timeOf(schedule), zone)
            .plusHours(12).toInstant().isAfter(horizon);
    }

    private LocalTime timeOf(MedicationSchedule schedule) {
        return schedule.getTimeOfDay() != null ? schedule.getTimeOfDay()
            : properties.reminders().defaultMedicationTime();
    }

    private static LocalDate nthDate(MedicationSchedule schedule, long n) {
        LocalDate start = schedule.getStartDate();
        return switch (schedule.getFrequency()) {
            case ONCE -> n == 0 ? start : null;
            case DAILY, TWICE_DAILY -> start.plusDays(n);
            case WEEKLY -> start.plusWeeks(n);
            case MONTHLY -> start.plusMonths(n);
        };
    }
}
