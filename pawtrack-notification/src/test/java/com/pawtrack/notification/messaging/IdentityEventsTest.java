package com.pawtrack.notification.messaging;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.pawtrack.notification.entity.HouseholdMemberId;
import com.pawtrack.notification.entity.Recipient;
import com.pawtrack.notification.entity.ReminderStatus;
import com.pawtrack.notification.repository.EmailLogRepository;
import com.pawtrack.notification.repository.HouseholdMemberRepository;
import com.pawtrack.notification.repository.PetRepository;
import com.pawtrack.notification.repository.RecipientRepository;
import com.pawtrack.notification.support.IntegrationTest;
import com.pawtrack.notification.support.Mailpit;
import com.pawtrack.notification.support.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** household.member.added/removed, household.deleted, household.invitation.created, user.updated. */
class IdentityEventsTest extends IntegrationTest {

    @Autowired
    RecipientRepository recipientRepository;

    @Autowired
    HouseholdMemberRepository memberRepository;

    @Autowired
    PetRepository petRepository;

    @Autowired
    EmailLogRepository emailLogRepository;

    private final Instant tomorrow = Instant.now().plus(1, ChronoUnit.DAYS);

    @Test
    void userUpdated_isAnUpsert() {
        TestUser user = TestUser.random("Alice");
        userUpdated(user, "Europe/Berlin", true);
        publish("user.updated", data("userId", user.id(), "email", "new@pawtrack.test", "displayName", "Ali",
            "timezone", "Not/AZone", "emailNotifications", false));

        Recipient recipient = recipientRepository.findById(user.id()).orElseThrow();
        assertThat(recipient.getEmail()).isEqualTo("new@pawtrack.test");
        assertThat(recipient.getDisplayName()).isEqualTo("Ali");
        assertThat(recipient.getTimezone()).as("invalid zone falls back to the default").isEqualTo("Europe/Sarajevo");
        assertThat(recipient.isEmailNotifications()).isFalse();
    }

    @Test
    void memberAdded_givesAccess_andMemberRemoved_takesItAway() throws Exception {
        TestUser owner = TestUser.random("Owner");
        TestUser member = TestUser.random("Member");
        TestHousehold home = household(owner);
        createReminder(owner, home.petId(), "Walk", tomorrow);

        mvc.perform(get("/api/v1/reminders").with(member.jwt())).andExpect(jsonPath("$", hasSize(0)));

        memberAdded(home.id(), member, "MEMBER");
        mvc.perform(get("/api/v1/reminders").with(member.jwt())).andExpect(jsonPath("$", hasSize(1)));

        publish("household.member.removed", data("householdId", home.id(), "userId", member.id()));
        mvc.perform(get("/api/v1/reminders").with(member.jwt())).andExpect(jsonPath("$", hasSize(0)));
        assertThat(memberRepository.existsById(new HouseholdMemberId(home.id(), owner.id()))).isTrue();
    }

    @Test
    void memberAdded_twice_updatesTheRole() {
        TestUser user = TestUser.random("User");
        UUID householdId = UUID.randomUUID();
        memberAdded(householdId, user, "SITTER");
        memberAdded(householdId, user, "OWNER");

        assertThat(memberRepository.findById(new HouseholdMemberId(householdId, user.id())).orElseThrow().getRole())
            .isEqualTo("OWNER");
    }

    @Test
    void householdDeleted_cancelsPendingReminders_deletesPetsAndMemberships() throws Exception {
        TestUser owner = TestUser.random("Owner");
        TestHousehold home = household(owner);
        UUID pending = createReminder(owner, home.petId(), "Walk", tomorrow);
        UUID done = createReminder(owner, home.petId(), "Feed", tomorrow);
        mvc.perform(post("/api/v1/reminders/{id}/done", done).with(owner.jwt())).andExpect(status().isOk());

        publish("household.deleted", data("householdId", home.id()));

        assertThat(reminderRepository.findById(pending).orElseThrow().getStatus()).isEqualTo(ReminderStatus.CANCELLED);
        assertThat(reminderRepository.findById(done).orElseThrow().getStatus()).isEqualTo(ReminderStatus.DONE);
        assertThat(petRepository.findById(home.petId()).orElseThrow().isDeleted()).isTrue();
        assertThat(memberRepository.findByHouseholdId(home.id())).isEmpty();
    }

    @Test
    void invitationCreated_sendsInviteEmail_evenWithoutAccount() {
        String email = "invitee-" + UUID.randomUUID() + "@pawtrack.test";
        UUID householdId = UUID.randomUUID();
        publish("household.invitation.created", data("householdId", householdId, "householdName", "Farm",
            "email", email, "code", "abc123CODE", "invitedBy", UUID.randomUUID(),
            "expiresAt", "2026-11-14T10:00:00Z"));

        List<Mailpit.Mail> mails = mailpit.awaitMails(email, 1);
        assertThat(mails).singleElement().satisfies(mail -> {
            assertThat(mail.subject()).isEqualTo("You're invited to join Farm on PawTrack");
            assertThat(mail.text()).contains("abc123CODE").contains("2026-11-14T10:00:00Z");
        });
        assertThat(emailLogRepository.findByReferenceId(householdId)).hasSize(1);
    }

    @Test
    void duplicateEvent_isProcessedOnce() throws Exception {
        String email = "invitee-" + UUID.randomUUID() + "@pawtrack.test";
        UUID eventId = UUID.randomUUID();
        var data = data("householdId", UUID.randomUUID(), "householdName", "Farm", "email", email,
            "code", "code-1", "invitedBy", UUID.randomUUID(), "expiresAt", "2026-11-14T10:00:00Z");

        send(eventId, "household.invitation.created", data);
        awaitProcessed(eventId);
        send(eventId, "household.invitation.created", data);
        // a later event on the same queue is processed after the duplicate
        publish("household.member.removed", data("householdId", UUID.randomUUID(), "userId", UUID.randomUUID()));

        assertThat(mailpit.to(email)).hasSize(1);
    }
}
