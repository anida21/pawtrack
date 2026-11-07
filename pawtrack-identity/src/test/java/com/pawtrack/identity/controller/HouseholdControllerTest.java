package com.pawtrack.identity.controller;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.pawtrack.identity.support.IntegrationTest;
import com.pawtrack.identity.support.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HouseholdControllerTest extends IntegrationTest {

    private static final String HOUSEHOLDS = "/api/v1/households";
    private static final String HOUSEHOLD = "/api/v1/households/{householdId}";
    private static final String MEMBER = "/api/v1/households/{householdId}/members/{userId}";

    // ---- POST /api/v1/households -----------------------------------------------------------------------------

    @Test
    void createHousehold_withoutToken_returns401() throws Exception {
        mvc.perform(post(HOUSEHOLDS).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Farm\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void createHousehold_makesCallerOwner() throws Exception {
        TestUser alice = TestUser.random("Alice");

        MockHttpServletResponse response = mvc.perform(post(HOUSEHOLDS).with(alice.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Farm\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.name").value("Farm"))
            .andExpect(jsonPath("$.createdAt").isNotEmpty())
            .andExpect(jsonPath("$.members", hasSize(1)))
            .andExpect(jsonPath("$.members[0].userId").value(alice.id().toString()))
            .andExpect(jsonPath("$.members[0].displayName").value("Alice"))
            .andExpect(jsonPath("$.members[0].email").value(alice.email()))
            .andExpect(jsonPath("$.members[0].role").value("OWNER"))
            .andExpect(jsonPath("$.members[0].joinedAt").isNotEmpty())
            .andReturn().getResponse();

        String id = JsonPath.read(response.getContentAsString(), "$.id");
        assertThat(response.getHeader("Location")).endsWith("/api/v1/households/" + id);
    }

    @Test
    void createHousehold_publishesMemberAddedForOwner() throws Exception {
        TestUser alice = TestUser.random("Alice");

        String householdId = createHousehold(alice, "Farm");

        var event = awaitEvent("household.member.added", data("householdId", householdId));
        assertThat(event.read("$.data.userId", String.class)).isEqualTo(alice.id().toString());
        assertThat(event.read("$.data.role", String.class)).isEqualTo("OWNER");
    }

    @Test
    void createHousehold_withBlankName_returns400() throws Exception {
        TestUser alice = TestUser.random("Alice");

        mvc.perform(post(HOUSEHOLDS).with(alice.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"  \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void createHousehold_withTooLongName_returns400() throws Exception {
        TestUser alice = TestUser.random("Alice");

        mvc.perform(post(HOUSEHOLDS).with(alice.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\"}".formatted("x".repeat(101))))
            .andExpect(status().isBadRequest());
    }

    // ---- GET /api/v1/households/{id} -------------------------------------------------------------------------

    @Test
    void getHousehold_withoutToken_returns401() throws Exception {
        mvc.perform(get(HOUSEHOLD, UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void getHousehold_asMember_returnsAllMembers() throws Exception {
        TestUser owner = TestUser.random("Owner");
        TestUser member = TestUser.random("Member");
        String householdId = createHousehold(owner, "Farm");
        join(owner, householdId, member, "MEMBER");

        mvc.perform(get(HOUSEHOLD, householdId).with(member.jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(householdId))
            .andExpect(jsonPath("$.name").value("Farm"))
            .andExpect(jsonPath("$.members[*].userId")
                .value(containsInAnyOrder(owner.id().toString(), member.id().toString())))
            .andExpect(jsonPath("$.members[?(@.userId == '%s')].role", member.id()).value(contains("MEMBER")));
    }

    @Test
    void getHousehold_asNonMember_returns403() throws Exception {
        TestUser owner = TestUser.random("Owner");
        TestUser stranger = TestUser.random("Stranger");
        String householdId = createHousehold(owner, "Farm");

        mvc.perform(get(HOUSEHOLD, householdId).with(stranger.jwt()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void getHousehold_unknownId_returns404() throws Exception {
        TestUser alice = TestUser.random("Alice");

        mvc.perform(get(HOUSEHOLD, UUID.randomUUID()).with(alice.jwt()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    // ---- PUT /api/v1/households/{id} -------------------------------------------------------------------------

    @Test
    void updateHousehold_withoutToken_returns401() throws Exception {
        mvc.perform(put(HOUSEHOLD, UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void updateHousehold_asOwner_renames() throws Exception {
        TestUser owner = TestUser.random("Owner");
        String householdId = createHousehold(owner, "Farm");

        mvc.perform(put(HOUSEHOLD, householdId).with(owner.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Big farm\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Big farm"));

        mvc.perform(get(HOUSEHOLD, householdId).with(owner.jwt()))
            .andExpect(jsonPath("$.name").value("Big farm"));
    }

    @Test
    void updateHousehold_asMember_returns403() throws Exception {
        TestUser owner = TestUser.random("Owner");
        TestUser member = TestUser.random("Member");
        String householdId = createHousehold(owner, "Farm");
        join(owner, householdId, member, "MEMBER");

        mvc.perform(put(HOUSEHOLD, householdId).with(member.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Mine now\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void updateHousehold_unknownId_returns404() throws Exception {
        TestUser alice = TestUser.random("Alice");

        mvc.perform(put(HOUSEHOLD, UUID.randomUUID()).with(alice.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"X\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void updateHousehold_withBlankName_returns400() throws Exception {
        TestUser owner = TestUser.random("Owner");
        String householdId = createHousehold(owner, "Farm");

        mvc.perform(put(HOUSEHOLD, householdId).with(owner.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest());
    }

    // ---- DELETE /api/v1/households/{id} ----------------------------------------------------------------------

    @Test
    void deleteHousehold_withoutToken_returns401() throws Exception {
        mvc.perform(delete(HOUSEHOLD, UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void deleteHousehold_asOwner_deletesItWithMembersAndInvitations() throws Exception {
        TestUser owner = TestUser.random("Owner");
        TestUser member = TestUser.random("Member");
        String householdId = createHousehold(owner, "Farm");
        join(owner, householdId, member, "MEMBER");
        TestUser invitee = TestUser.random("Invitee");
        String pendingCode = invite(owner, householdId, invitee.email(), "SITTER");

        mvc.perform(delete(HOUSEHOLD, householdId).with(owner.jwt()))
            .andExpect(status().isNoContent());

        var event = awaitEvent("household.deleted", data("householdId", householdId));
        assertThat(event.read("$.producer", String.class)).isEqualTo("pawtrack-identity");

        mvc.perform(get(HOUSEHOLD, householdId).with(owner.jwt()))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/me/households").with(member.jwt()))
            .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(post("/api/v1/invitations/{code}/accept", pendingCode).with(invitee.jwt()))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteHousehold_asMember_returns403() throws Exception {
        TestUser owner = TestUser.random("Owner");
        TestUser member = TestUser.random("Member");
        String householdId = createHousehold(owner, "Farm");
        join(owner, householdId, member, "MEMBER");

        mvc.perform(delete(HOUSEHOLD, householdId).with(member.jwt()))
            .andExpect(status().isForbidden());

        mvc.perform(get(HOUSEHOLD, householdId).with(owner.jwt()))
            .andExpect(status().isOk());
    }

    @Test
    void deleteHousehold_asNonMember_returns403() throws Exception {
        TestUser owner = TestUser.random("Owner");
        TestUser stranger = TestUser.random("Stranger");
        String householdId = createHousehold(owner, "Farm");

        mvc.perform(delete(HOUSEHOLD, householdId).with(stranger.jwt()))
            .andExpect(status().isForbidden());
    }

    @Test
    void deleteHousehold_unknownId_returns404() throws Exception {
        TestUser alice = TestUser.random("Alice");

        mvc.perform(delete(HOUSEHOLD, UUID.randomUUID()).with(alice.jwt()))
            .andExpect(status().isNotFound());
    }

    // ---- DELETE /api/v1/households/{id}/members/{userId} -----------------------------------------------------

    @Test
    void removeMember_withoutToken_returns401() throws Exception {
        mvc.perform(delete(MEMBER, UUID.randomUUID(), UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void removeMember_ownerRemovesMember() throws Exception {
        TestUser owner = TestUser.random("Owner");
        TestUser member = TestUser.random("Member");
        String householdId = createHousehold(owner, "Farm");
        join(owner, householdId, member, "MEMBER");

        mvc.perform(delete(MEMBER, householdId, member.id()).with(owner.jwt()))
            .andExpect(status().isNoContent());

        var event = awaitEvent("household.member.removed", data("householdId", householdId));
        assertThat(event.read("$.data.userId", String.class)).isEqualTo(member.id().toString());

        mvc.perform(get(HOUSEHOLD, householdId).with(member.jwt()))
            .andExpect(status().isForbidden());
        mvc.perform(get(HOUSEHOLD, householdId).with(owner.jwt()))
            .andExpect(jsonPath("$.members", hasSize(1)));
    }

    @Test
    void removeMember_memberLeavesThemself() throws Exception {
        TestUser owner = TestUser.random("Owner");
        TestUser sitter = TestUser.random("Sitter");
        String householdId = createHousehold(owner, "Farm");
        join(owner, householdId, sitter, "SITTER");

        mvc.perform(delete(MEMBER, householdId, sitter.id()).with(sitter.jwt()))
            .andExpect(status().isNoContent());

        awaitEvent("household.member.removed", data("userId", sitter.id()));
        mvc.perform(get(HOUSEHOLD, householdId).with(sitter.jwt()))
            .andExpect(status().isForbidden());
    }

    @Test
    void removeMember_memberRemovingSomeoneElse_returns403() throws Exception {
        TestUser owner = TestUser.random("Owner");
        TestUser member = TestUser.random("Member");
        TestUser sitter = TestUser.random("Sitter");
        String householdId = createHousehold(owner, "Farm");
        join(owner, householdId, member, "MEMBER");
        join(owner, householdId, sitter, "SITTER");

        mvc.perform(delete(MEMBER, householdId, sitter.id()).with(member.jwt()))
            .andExpect(status().isForbidden());
        mvc.perform(delete(MEMBER, householdId, owner.id()).with(member.jwt()))
            .andExpect(status().isForbidden());
    }

    @Test
    void removeMember_asNonMember_returns403() throws Exception {
        TestUser owner = TestUser.random("Owner");
        TestUser stranger = TestUser.random("Stranger");
        String householdId = createHousehold(owner, "Farm");

        mvc.perform(delete(MEMBER, householdId, owner.id()).with(stranger.jwt()))
            .andExpect(status().isForbidden());
    }

    @Test
    void removeMember_lastOwnerLeaving_returns409() throws Exception {
        TestUser owner = TestUser.random("Owner");
        TestUser member = TestUser.random("Member");
        String householdId = createHousehold(owner, "Farm");
        join(owner, householdId, member, "MEMBER");

        mvc.perform(delete(MEMBER, householdId, owner.id()).with(owner.jwt()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409));

        mvc.perform(get(HOUSEHOLD, householdId).with(owner.jwt()))
            .andExpect(jsonPath("$.members", hasSize(2)));
    }

    @Test
    void removeMember_userNotInHousehold_returns404() throws Exception {
        TestUser owner = TestUser.random("Owner");
        String householdId = createHousehold(owner, "Farm");

        mvc.perform(delete(MEMBER, householdId, UUID.randomUUID()).with(owner.jwt()))
            .andExpect(status().isNotFound());
    }

    @Test
    void removeMember_unknownHousehold_returns404() throws Exception {
        TestUser alice = TestUser.random("Alice");

        mvc.perform(delete(MEMBER, UUID.randomUUID(), alice.id()).with(alice.jwt()))
            .andExpect(status().isNotFound());
    }
}
