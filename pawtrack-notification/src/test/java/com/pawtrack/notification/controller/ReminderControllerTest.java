package com.pawtrack.notification.controller;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.pawtrack.notification.entity.ReminderStatus;
import com.pawtrack.notification.support.IntegrationTest;
import com.pawtrack.notification.support.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReminderControllerTest extends IntegrationTest {

    private static final String REMINDERS = "/api/v1/reminders";
    private static final String REMINDER = "/api/v1/reminders/{id}";
    private static final String DONE = "/api/v1/reminders/{id}/done";

    private final Instant tomorrow = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

    // ---- GET /api/v1/reminders -----------------------------------------------------------------------------

    @Test
    void list_withoutToken_returns401() throws Exception {
        mvc.perform(get(REMINDERS)).andExpect(status().isUnauthorized());
    }

    @Test
    void list_userWithoutHouseholds_returnsEmptyList() throws Exception {
        mvc.perform(get(REMINDERS).with(TestUser.random("Nobody").jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void list_returnsOnlyRemindersOfTheCallersHouseholds_sortedByDueAt() throws Exception {
        TestUser owner = TestUser.random("Owner");
        TestUser member = TestUser.random("Member");
        TestHousehold home = household(owner, member);
        TestHousehold other = household(TestUser.random("Stranger"));
        UUID later = createReminder(owner, home.petId(), "Later", tomorrow.plus(1, ChronoUnit.DAYS));
        UUID sooner = createReminder(member, home.petId(), "Sooner", tomorrow);
        createReminder(other.owner(), other.petId(), "Not mine", tomorrow);

        mvc.perform(get(REMINDERS).with(member.jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", contains(sooner.toString(), later.toString())));
    }

    @Test
    void list_filtersByPetStatusAndDueAtRange() throws Exception {
        TestUser owner = TestUser.random("Owner");
        TestHousehold home = household(owner);
        UUID secondPet = petCreated(home.id(), "Mia");
        UUID first = createReminder(owner, home.petId(), "First", tomorrow);
        UUID second = createReminder(owner, home.petId(), "Second", tomorrow.plus(2, ChronoUnit.DAYS));
        UUID otherPet = createReminder(owner, secondPet, "Other pet", tomorrow);
        mvc.perform(post(DONE, second).with(owner.jwt())).andExpect(status().isOk());

        mvc.perform(get(REMINDERS).param("petId", secondPet.toString()).with(owner.jwt()))
            .andExpect(jsonPath("$[*].id", contains(otherPet.toString())));
        mvc.perform(get(REMINDERS).param("petId", home.petId().toString()).param("status", "DONE")
                .with(owner.jwt()))
            .andExpect(jsonPath("$[*].id", contains(second.toString())));
        // from inclusive, to exclusive
        mvc.perform(get(REMINDERS).param("petId", home.petId().toString())
                .param("from", tomorrow.toString())
                .param("to", tomorrow.plus(2, ChronoUnit.DAYS).toString())
                .with(owner.jwt()))
            .andExpect(jsonPath("$[*].id", contains(first.toString())));
    }

    @Test
    void list_fromAfterTo_returns400() throws Exception {
        mvc.perform(get(REMINDERS).param("from", tomorrow.toString()).param("to", Instant.now().toString())
                .with(TestUser.random("U").jwt()))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void list_unknownStatus_returns400() throws Exception {
        mvc.perform(get(REMINDERS).param("status", "LATER").with(TestUser.random("U").jwt()))
            .andExpect(status().isBadRequest());
    }

    // ---- POST /api/v1/reminders ----------------------------------------------------------------------------

    @Test
    void create_withoutToken_returns401() throws Exception {
        mvc.perform(post(REMINDERS).contentType(MediaType.APPLICATION_JSON)
                .content("{\"petId\":\"%s\",\"title\":\"x\",\"dueAt\":\"%s\"}".formatted(UUID.randomUUID(), tomorrow)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void create_asMember_returnsCustomPendingReminder() throws Exception {
        TestUser owner = TestUser.random("Owner");
        TestUser sitter = TestUser.random("Sitter");
        TestHousehold home = household(owner);
        memberAdded(home.id(), sitter, "SITTER");

        mvc.perform(post(REMINDERS).with(sitter.jwt()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"petId":"%s","title":"Buy food","message":"The big bag","dueAt":"%s"}"""
                    .formatted(home.petId(), tomorrow)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.petId").value(home.petId().toString()))
            .andExpect(jsonPath("$.householdId").value(home.id().toString()))
            .andExpect(jsonPath("$.title").value("Buy food"))
            .andExpect(jsonPath("$.message").value("The big bag"))
            .andExpect(jsonPath("$.dueAt").value(tomorrow.toString()))
            .andExpect(jsonPath("$.source").value("CUSTOM"))
            .andExpect(jsonPath("$.sourceId").value(nullValue()))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.sentAt").value(nullValue()));
    }

    @Test
    void create_invalidBody_returns400() throws Exception {
        TestUser owner = TestUser.random("Owner");
        TestHousehold home = household(owner);
        String tooLong = "x".repeat(121);

        for (String body : new String[] {
            "{\"title\":\"x\",\"dueAt\":\"%s\"}".formatted(tomorrow),
            "{\"petId\":\"%s\",\"title\":\" \",\"dueAt\":\"%s\"}".formatted(home.petId(), tomorrow),
            "{\"petId\":\"%s\",\"title\":\"%s\",\"dueAt\":\"%s\"}".formatted(home.petId(), tooLong, tomorrow),
            "{\"petId\":\"%s\",\"title\":\"x\"}".formatted(home.petId()),
            "{\"petId\":\"%s\",\"title\":\"x\",\"dueAt\":\"tomorrow\"}".formatted(home.petId())}) {
            mvc.perform(post(REMINDERS).with(owner.jwt()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
    }

    @Test
    void create_unknownPet_returns404() throws Exception {
        mvc.perform(post(REMINDERS).with(TestUser.random("U").jwt()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"petId\":\"%s\",\"title\":\"x\",\"dueAt\":\"%s\"}".formatted(UUID.randomUUID(), tomorrow)))
            .andExpect(status().isNotFound());
    }

    @Test
    void create_deletedPet_returns404() throws Exception {
        TestUser owner = TestUser.random("Owner");
        TestHousehold home = household(owner);
        publish("pet.deleted", data("petId", home.petId(), "householdId", home.id()));

        mvc.perform(post(REMINDERS).with(owner.jwt()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"petId\":\"%s\",\"title\":\"x\",\"dueAt\":\"%s\"}".formatted(home.petId(), tomorrow)))
            .andExpect(status().isNotFound());
    }

    @Test
    void create_petOfAnotherHousehold_returns403() throws Exception {
        TestHousehold other = household(TestUser.random("Stranger"));

        mvc.perform(post(REMINDERS).with(TestUser.random("U").jwt()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"petId\":\"%s\",\"title\":\"x\",\"dueAt\":\"%s\"}".formatted(other.petId(), tomorrow)))
            .andExpect(status().isForbidden());
    }

    // ---- DELETE /api/v1/reminders/{id} ---------------------------------------------------------------------

    @Test
    void cancel_withoutToken_returns401() throws Exception {
        mvc.perform(delete(REMINDER, UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void cancel_setsStatusCancelled_andIsIdempotent() throws Exception {
        TestUser owner = TestUser.random("Owner");
        TestHousehold home = household(owner);
        UUID id = createReminder(owner, home.petId(), "Walk", tomorrow);

        mvc.perform(delete(REMINDER, id).with(owner.jwt())).andExpect(status().isNoContent());
        mvc.perform(delete(REMINDER, id).with(owner.jwt())).andExpect(status().isNoContent());

        assertThat(reminderRepository.findById(id).orElseThrow().getStatus()).isEqualTo(ReminderStatus.CANCELLED);
    }

    @Test
    void cancel_doneReminder_returns409() throws Exception {
        TestUser owner = TestUser.random("Owner");
        TestHousehold home = household(owner);
        UUID id = createReminder(owner, home.petId(), "Walk", tomorrow);
        mvc.perform(post(DONE, id).with(owner.jwt())).andExpect(status().isOk());

        mvc.perform(delete(REMINDER, id).with(owner.jwt())).andExpect(status().isConflict());
    }

    @Test
    void cancel_unknownReminder_returns404() throws Exception {
        mvc.perform(delete(REMINDER, UUID.randomUUID()).with(TestUser.random("U").jwt()))
            .andExpect(status().isNotFound());
    }

    @Test
    void cancel_reminderOfAnotherHousehold_returns403() throws Exception {
        TestHousehold other = household(TestUser.random("Stranger"));
        UUID id = createReminder(other.owner(), other.petId(), "Walk", tomorrow);

        mvc.perform(delete(REMINDER, id).with(TestUser.random("U").jwt())).andExpect(status().isForbidden());
        assertThat(reminderRepository.findById(id).orElseThrow().getStatus()).isEqualTo(ReminderStatus.PENDING);
    }

    @Test
    void cancel_invalidId_returns400() throws Exception {
        mvc.perform(delete(REMINDER, "not-a-uuid").with(TestUser.random("U").jwt()))
            .andExpect(status().isBadRequest());
    }

    // ---- POST /api/v1/reminders/{id}/done ------------------------------------------------------------------

    @Test
    void done_withoutToken_returns401() throws Exception {
        mvc.perform(post(DONE, UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void done_returnsDoneReminder_andIsIdempotent() throws Exception {
        TestUser owner = TestUser.random("Owner");
        TestUser member = TestUser.random("Member");
        TestHousehold home = household(owner, member);
        UUID id = createReminder(owner, home.petId(), "Walk", tomorrow);

        mvc.perform(post(DONE, id).with(member.jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.status").value("DONE"));
        mvc.perform(post(DONE, id).with(member.jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DONE"));
    }

    @Test
    void done_cancelledReminder_returns409() throws Exception {
        TestUser owner = TestUser.random("Owner");
        TestHousehold home = household(owner);
        UUID id = createReminder(owner, home.petId(), "Walk", tomorrow);
        mvc.perform(delete(REMINDER, id).with(owner.jwt())).andExpect(status().isNoContent());

        mvc.perform(post(DONE, id).with(owner.jwt())).andExpect(status().isConflict());
    }

    @Test
    void done_unknownReminder_returns404() throws Exception {
        mvc.perform(post(DONE, UUID.randomUUID()).with(TestUser.random("U").jwt()))
            .andExpect(status().isNotFound());
    }

    @Test
    void done_reminderOfAnotherHousehold_returns403() throws Exception {
        TestHousehold other = household(TestUser.random("Stranger"));
        UUID id = createReminder(other.owner(), other.petId(), "Walk", tomorrow);

        mvc.perform(post(DONE, id).with(TestUser.random("U").jwt())).andExpect(status().isForbidden());
    }
}
