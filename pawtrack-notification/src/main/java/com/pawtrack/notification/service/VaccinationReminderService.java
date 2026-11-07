package com.pawtrack.notification.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import com.pawtrack.notification.config.PawtrackProperties;
import com.pawtrack.notification.entity.Reminder;
import com.pawtrack.notification.entity.ReminderSource;
import com.pawtrack.notification.messaging.ConsumedEvent.VaccinationRecorded;
import com.pawtrack.notification.repository.ReminderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code health.vaccination.recorded} (create and every update): the vaccination's PENDING reminders are
 * replaced by "7 days before" and "on the day" reminders at 09:00 household time, or just cancelled when
 * the event has no {@code nextDueDate}. Reminders that would already be due are not created.
 */
@Service
@RequiredArgsConstructor
public class VaccinationReminderService {

    private final ReminderRepository reminderRepository;
    private final HouseholdTimeZones timeZones;
    private final PetNames petNames;
    private final PawtrackProperties properties;
    private final Clock clock;

    @Transactional
    public void onRecorded(VaccinationRecorded event) {
        Instant now = clock.instant();
        reminderRepository.cancelPendingBySource(ReminderSource.VACCINATION, event.vaccinationId(), now);
        if (event.nextDueDate() == null || petNames.isDeleted(event.petId())) {
            return;
        }
        ZoneId zone = timeZones.zoneOf(event.householdId());
        LocalDate due = event.nextDueDate();
        int leadDays = properties.reminders().vaccinationLeadDays();
        String pet = petNames.nameOf(event.petId());
        String vaccine = event.vaccine() == null ? "Vaccination" : event.vaccine();
        String message = "%s's %s vaccination is due on %s.".formatted(pet, vaccine, due);

        List<Reminder> reminders = List.of(
            reminder(event, at(due.minusDays(leadDays), zone),
                "%s: %s vaccination due in %d days".formatted(pet, vaccine, leadDays), message),
            reminder(event, at(due, zone),
                "%s: %s vaccination due today".formatted(pet, vaccine), message));
        reminderRepository.saveAll(reminders.stream().filter(r -> r.getDueAt().isAfter(now)).toList());
    }

    private Instant at(LocalDate date, ZoneId zone) {
        return ZonedDateTime.of(date, properties.reminders().vaccinationTime(), zone).toInstant();
    }

    private static Reminder reminder(VaccinationRecorded event, Instant dueAt, String title, String message) {
        return new Reminder(event.petId(), event.householdId(), title, message, dueAt,
            ReminderSource.VACCINATION, event.vaccinationId(), null);
    }
}
