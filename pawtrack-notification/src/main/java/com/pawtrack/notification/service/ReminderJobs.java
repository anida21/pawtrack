package com.pawtrack.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Timed jobs; crons are in {@code pawtrack.reminders.*} ("-" disables a job, e.g. in tests). */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderJobs {

    private final ReminderDispatcher dispatcher;
    private final MedicationReminderService medicationReminders;

    @Scheduled(cron = "${pawtrack.reminders.dispatch-cron}")
    public void dispatchDueReminders() {
        int sent = dispatcher.dispatchDue();
        if (sent > 0) {
            log.info("Sent {} due reminder(s)", sent);
        }
    }

    @Scheduled(cron = "${pawtrack.reminders.medication-top-up-cron}")
    public void topUpMedicationReminders() {
        int created = medicationReminders.topUpAll();
        if (created > 0) {
            log.info("Generated {} medication reminder(s)", created);
        }
    }
}
