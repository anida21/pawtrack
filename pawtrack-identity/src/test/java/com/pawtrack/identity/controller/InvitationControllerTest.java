package com.pawtrack.identity.controller;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.pawtrack.identity.entity.HouseholdRole;
import com.pawtrack.identity.entity.Invitation;
import com.pawtrack.identity.repository.HouseholdRepository;
import com.pawtrack.identity.repository.InvitationRepository;
import com.pawtrack.identity.support.IntegrationTest;
import com.pawtrack.identity.support.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InvitationControllerTest extends IntegrationTest {

    private static final String INVITATIONS = "/api/v1/households/{householdId}/invitations";
    private static final String ACCEPT = "/api/v1/invitations/{code}/accept";

    @Autowired
    InvitationRepository invitationRepository;

    @Autowired
    HouseholdRepository householdRepository;

    // ---- POST /api/v1/households/{id}/invitations ------------------------------------------------------------

    @Test
    void createInvitation_withoutToken_returns401() throws Exception {
        mvc.perform(post(INVITATIONS, UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@pawtrack.test\",\"role\":\"MEMBER\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void createInvitation_asOwner_returnsCodeValidForSevenDays() throws Exception {
        TestUser owner = TestUser.random("Owner");
        String householdId = createHousehold(owner, "Farm");

        String body = mvc.perform(post(INVITATIONS, householdId).with(owner.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"Guest@PawTrack.test\",\"role\":\"SITTER\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("guest@pawtrack.test"))
            .andExpect(jsonPath("$.role").value("SITTER"))
            .andReturn().getResponse().getContentAsString();

        DocumentContext invitation = JsonPath.parse(body);
        assertThat(invitation.read("$.code", String.class)).matches("[A-Za-z0-9_-]{43}");
        assertThat(Instant.parse(invitation.read("$.expiresAt", String.class)))
            .isCloseTo(Instant.now().plus(Duration.ofDays(7)), within(Duration.ofMinutes(1)));
    }

    @Test
    void createInvitation_publishesInvitationCreated() throws Exception {
        TestUser owner = TestUser.random("Owner");
        String householdId = createHousehold(owner, "Farm");

        String code = invite(owner, householdId, "guest@pawtrack.test", "MEMBER");

        DocumentContext event = awaitEvent("household.invitation.created", data("code", code));
        assertThat(event.read("$.data.householdId", String.class)).isEqualTo(householdId);
        assertThat(event.read("$.data.householdName", String.class)).isEqualTo("Farm");
        assertThat(event.read("$.data.email", String.class)).isEqualTo("guest@pawtrack.test");
        assertThat(event.read("$.data.invitedBy", String.class)).isEqualTo(owner.id().toString());
        assertThat(event.read("$.data.expiresAt", String.class)).isNotBlank();
    }

    @Test
    void createInvitation_codesAreUnique() throws Exception {
        TestUser owner = TestUser.random("Owner");
        String householdId = createHousehold(owner, "Farm");

        String first = invite(owner, householdId, "guest@pawtrack.test", "MEMBER");
        String second = invite(owner, householdId, "guest@pawtrack.test", "MEMBER");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void createInvitation_asMember_returns403() throws Exception {
        TestUser owner = TestUser.random("Owner");
        TestUser member = TestUser.random("Member");
        String householdId = createHousehold(owner, "Farm");
        join(owner, householdId, member, "MEMBER");

        mvc.perform(post(INVITATIONS, householdId).with(member.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"friend@pawtrack.test\",\"role\":\"MEMBER\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void createInvitation_asNonMember_returns403() throws Exception {
        TestUser owner = TestUser.random("Owner");
        TestUser stranger = TestUser.random("Stranger");
        String householdId = createHousehold(owner, "Farm");

        mvc.perform(post(INVITATIONS, householdId).with(stranger.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"friend@pawtrack.test\",\"role\":\"MEMBER\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void createInvitation_unknownHousehold_returns404() throws Exception {
        TestUser alice = TestUser.random("Alice");

        mvc.perform(post(INVITATIONS, UUID.randomUUID()).with(alice.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"friend@pawtrack.test\",\"role\":\"MEMBER\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void createInvitation_forOwnerRole_returns400() throws Exception {
        TestUser owner = TestUser.random("Owner");
        String householdId = createHousehold(owner, "Farm");

        mvc.perform(post(INVITATIONS, householdId).with(owner.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"friend@pawtrack.test\",\"role\":\"OWNER\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createInvitation_withInvalidEmail_returns400() throws Exception {
        TestUser owner = TestUser.random("Owner");
        String householdId = createHousehold(owner, "Farm");

        mvc.perform(post(INVITATIONS, householdId).with(owner.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\",\"role\":\"MEMBER\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createInvitation_withoutRole_returns400() throws Exception {
        TestUser owner = TestUser.random("Owner");
        String householdId = createHousehold(owner, "Farm");

        mvc.perform(post(INVITATIONS, householdId).with(owner.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"friend@pawtrack.test\"}"))
            .andExpect(status().isBadRequest());
    }

    // ---- POST /api/v1/invitations/{code}/accept --------------------------------------------------------------

    @Test
    void acceptInvitation_withoutToken_returns401() throws Exception {
        mvc.perform(post(ACCEPT, "some-code")).andExpect(status().isUnauthorized());
    }

    @Test
    void acceptInvitation_joinsHouseholdWithInvitedRole() throws Exception {
        TestUser owner = TestUser.random("Owner");
        TestUser guest = TestUser.random("Guest");
        String householdId = createHousehold(owner, "Farm");
        String code = invite(owner, householdId, guest.email(), "SITTER");

        mvc.perform(post(ACCEPT, code).with(guest.jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(householdId))
            .andExpect(jsonPath("$.name").value("Farm"))
            .andExpect(jsonPath("$.members", hasSize(2)))
            .andExpect(jsonPath("$.members[?(@.userId == '%s')].role", guest.id()).value(contains("SITTER")));

        DocumentContext event = awaitEvent("household.member.added",
            data("householdId", householdId).and(data("userId", guest.id())));
        assertThat(event.read("$.data.role", String.class)).isEqualTo("SITTER");
    }

    @Test
    void acceptInvitation_matchesEmailCaseInsensitively() throws Exception {
        TestUser owner = TestUser.random("Owner");
        TestUser guest = TestUser.random("Guest");
        String householdId = createHousehold(owner, "Farm");
        String code = invite(owner, householdId, guest.email().toUpperCase(), "MEMBER");

        mvc.perform(post(ACCEPT, code).with(guest.jwt()))
            .andExpect(status().isOk());
    }

    @Test
    void acceptInvitation_forDifferentEmail_returns403() throws Exception {
        TestUser owner = TestUser.random("Owner");
        TestUser guest = TestUser.random("Guest");
        TestUser someoneElse = TestUser.random("Someone else");
        String householdId = createHousehold(owner, "Farm");
        String code = invite(owner, householdId, guest.email(), "MEMBER");

        mvc.perform(post(ACCEPT, code).with(someoneElse.jwt()))
            .andExpect(status().isForbidden());

        // the code is still usable by the right person
        mvc.perform(post(ACCEPT, code).with(guest.jwt()))
            .andExpect(status().isOk());
    }

    @Test
    void acceptInvitation_unknownCode_returns404() throws Exception {
        TestUser guest = TestUser.random("Guest");

        mvc.perform(post(ACCEPT, "does-not-exist").with(guest.jwt()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void acceptInvitation_expired_returns410() throws Exception {
        TestUser owner = TestUser.random("Owner");
        TestUser guest = TestUser.random("Guest");
        String householdId = createHousehold(owner, "Farm");
        String code = "expired-" + UUID.randomUUID();
        invitationRepository.save(new Invitation(
            householdRepository.getReferenceById(UUID.fromString(householdId)), code, guest.email(),
            HouseholdRole.MEMBER, owner.id(), Instant.now().minus(Duration.ofMinutes(1))));

        mvc.perform(post(ACCEPT, code).with(guest.jwt()))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.status").value(410));

        mvc.perform(get("/api/v1/households/{id}", householdId).with(guest.jwt()))
            .andExpect(status().isForbidden());
    }

    @Test
    void acceptInvitation_alreadyUsed_returns410() throws Exception {
        TestUser owner = TestUser.random("Owner");
        TestUser guest = TestUser.random("Guest");
        String householdId = createHousehold(owner, "Farm");
        String code = invite(owner, householdId, guest.email(), "MEMBER");

        mvc.perform(post(ACCEPT, code).with(guest.jwt())).andExpect(status().isOk());
        mvc.perform(post(ACCEPT, code).with(guest.jwt())).andExpect(status().isGone());
    }

    @Test
    void acceptInvitation_whenAlreadyMember_keepsExistingRole() throws Exception {
        TestUser owner = TestUser.random("Owner");
        String householdId = createHousehold(owner, "Farm");
        String code = invite(owner, householdId, owner.email(), "SITTER");

        mvc.perform(post(ACCEPT, code).with(owner.jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.members", hasSize(1)))
            .andExpect(jsonPath("$.members[0].role").value("OWNER"));
    }
}
