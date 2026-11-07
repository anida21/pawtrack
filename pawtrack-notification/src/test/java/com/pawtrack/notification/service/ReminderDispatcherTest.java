package com.pawtrack.notification.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.pawtrack.notification.entity.EmailLog;
import com.pawtrack.notification.entity.Reminder;
import com.pawtrack.notification.entity.ReminderStatus;
import com.pawtrack.notification.repository.EmailLogRepository;
import com.pawtrack.notification.support.IntegrationTest;
import com.pawtrack.notification.support.Mailpit;
import com.pawtrack.notification.support.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The scheduler's work: email due reminders, respect emailNotifications, mark SENT. */
class ReminderDispatcherTest extends IntegrationTest {

    @Autowired
    ReminderDispatcher dispatcher;

    @Autowired
    EmailLogRepository emailLogRepository;

    @Test
    void dueReminder_isEmailedToMembersWhoWantEmail_andMarkedSent() throws Exception {
        TestUser owner = TestUser.random("Owner");
        TestUser member = TestUser.random("Member");
        TestUser quiet = TestUser.random("Quiet");
        TestHousehold home = household(owner, member, quiet);
        userUpdated(quiet, "UTC", false);
        UUID id = createReminder(owner, home.petId(), "Give worm tablet", Instant.now().minusSeconds(60));

        dispatcher.dispatchDue();

        Reminder reminder = reminderRepository.findById(id).orElseThrow();
        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.SENT);
        assertThat(reminder.getSentAt()).isNotNull();
        for (TestUser user : List.of(owner, member)) {
            assertThat(mailpit.awaitMails(user.email(), 1)).singleElement().satisfies(mail -> {
                assertThat(mail.subject()).isEqualTo("PawTrack reminder: Give worm tablet");
                assertThat(mail.text()).contains("Hello " + user.name()).contains("Pet: Rex");
            });
        }
        assertThat(mailpit.to(quiet.email())).isEmpty();
        assertThat(emailLogRepository.findByReferenceId(id)).extracting(EmailLog::getRecipient)
            .containsExactlyInAnyOrder(owner.email(), member.email());

        mvc.perform(get("/api/v1/reminders").param("status", "SENT").with(owner.jwt()))
            .andExpect(jsonPath("$[0].id").value(id.toString()))
            .andExpect(jsonPath("$[0].sentAt").isNotEmpty());
    }

    @Test
    void reminderNotDueYet_staysPending() throws Exception {
        TestUser owner = TestUser.random("Owner");
        TestHousehold home = household(owner);
        UUID id = createReminder(owner, home.petId(), "Later", Instant.now().plus(1, ChronoUnit.HOURS));

        dispatcher.dispatchDue();

        assertThat(reminderRepository.findById(id).orElseThrow().getStatus()).isEqualTo(ReminderStatus.PENDING);
        assertThat(mailpit.to(owner.email())).isEmpty();
    }

    @Test
    void dueReminderWithoutRecipients_isMarkedSentWithoutEmail() throws Exception {
        TestUser owner = TestUser.random("Owner");
        TestHousehold home = household(owner);
        userUpdated(owner, "UTC", false);
        UUID id = createReminder(owner, home.petId(), "Nobody hears", Instant.now().minusSeconds(1));

        dispatcher.dispatchDue();

        assertThat(reminderRepository.findById(id).orElseThrow().getStatus()).isEqualTo(ReminderStatus.SENT);
        assertThat(emailLogRepository.findByReferenceId(id)).isEmpty();
    }

    @Test
    void doneAndCancelledReminders_areNotSent() throws Exception {
        TestUser owner = TestUser.random("Owner");
        TestHousehold home = household(owner);
        UUID done = createReminder(owner, home.petId(), "Done", Instant.now().minusSeconds(1));
        UUID cancelled = createReminder(owner, home.petId(), "Cancelled", Instant.now().minusSeconds(1));
        mvc.perform(post("/api/v1/reminders/{id}/done", done).with(owner.jwt())).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/reminders/{id}", cancelled).with(owner.jwt())).andExpect(status().isNoContent());

        dispatcher.dispatchDue();

        assertThat(reminderRepository.findById(done).orElseThrow().getStatus()).isEqualTo(ReminderStatus.DONE);
        assertThat(reminderRepository.findById(cancelled).orElseThrow().getStatus())
            .isEqualTo(ReminderStatus.CANCELLED);
        List<Mailpit.Mail> mails = mailpit.to(owner.email());
        assertThat(mails).isEmpty();
    }
}
