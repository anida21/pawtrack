package com.pawtrack.notification.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.pawtrack.notification.config.PawtrackProperties;
import com.pawtrack.notification.entity.EmailLog;
import com.pawtrack.notification.entity.Recipient;
import com.pawtrack.notification.entity.Reminder;
import com.pawtrack.notification.repository.RecipientRepository;
import com.pawtrack.notification.repository.ReminderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emails due PENDING reminders to every household member with {@code emailNotifications=true}, then marks
 * them SENT (also when nobody wants email, so they do not stay due forever). If the SMTP server rejects a
 * message the reminder stays PENDING and is tried again on the next run.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderDispatcher {

    private static final DateTimeFormatter DUE_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm (z)");

    private final ReminderRepository reminderRepository;
    private final RecipientRepository recipientRepository;
    private final PetNames petNames;
    private final MailService mailService;
    private final PawtrackProperties properties;
    private final Clock clock;

    /** @return number of reminders marked SENT */
    @Transactional
    public int dispatchDue() {
        Instant now = clock.instant();
        List<Reminder> due = reminderRepository.lockDue(now, properties.reminders().dispatchBatchSize());
        int sent = 0;
        for (Reminder reminder : due) {
            try {
                for (Recipient recipient : recipientRepository.findEmailRecipients(reminder.getHouseholdId())) {
                    mailService.send(EmailLog.Kind.REMINDER, reminder.getId(), recipient.getEmail(),
                        "PawTrack reminder: " + reminder.getTitle(), body(reminder, recipient));
                }
                reminder.markSent(now);
                sent++;
            } catch (MailException ex) {
                log.warn("Could not email reminder {}, will retry on the next run", reminder.getId(), ex);
            }
        }
        return sent;
    }

    private String body(Reminder reminder, Recipient recipient) {
        ZoneId zone = HouseholdTimeZones.parse(recipient.getTimezone())
            .orElse(properties.reminders().defaultTimezone());
        String greeting = recipient.getDisplayName() == null ? "Hello" : "Hello " + recipient.getDisplayName();
        return """
            %s,

            %s
            %s
            Pet: %s
            Due: %s

            Mark it as done or manage your reminders at %s

            -- PawTrack (you can turn these emails off in your profile)
            """.formatted(greeting, reminder.getTitle(),
                reminder.getMessage() == null ? "" : reminder.getMessage() + "\n",
                petNames.nameOf(reminder.getPetId()), DUE_FORMAT.format(reminder.getDueAt().atZone(zone)),
                mailService.appUrl());
    }
}
