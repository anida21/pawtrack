package com.pawtrack.identity.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.jayway.jsonpath.DocumentContext;
import com.pawtrack.identity.support.IntegrationTest;
import com.pawtrack.identity.support.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MeControllerTest extends IntegrationTest {

    // ---- GET /api/v1/me --------------------------------------------------------------------------------------

    @Test
    void getMe_withoutToken_returns401() throws Exception {
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void getMe_firstCall_provisionsUserAndDefaultHousehold() throws Exception {
        TestUser alice = TestUser.random("Alice");

        mvc.perform(get("/api/v1/me").with(alice.jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(alice.id().toString()))
            .andExpect(jsonPath("$.email").value(alice.email()))
            .andExpect(jsonPath("$.displayName").value("Alice"))
            .andExpect(jsonPath("$.timezone").value("Europe/Sarajevo"))
            .andExpect(jsonPath("$.emailNotifications").value(true))
            .andExpect(jsonPath("$.createdAt").isNotEmpty());

        mvc.perform(get("/api/v1/me/households").with(alice.jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].householdId").isNotEmpty())
            .andExpect(jsonPath("$[0].householdName").value("My pets"))
            .andExpect(jsonPath("$[0].role").value("OWNER"));
    }

    @Test
    void getMe_firstCall_publishesUserAndMembershipEvents() throws Exception {
        TestUser alice = TestUser.random("Alice");

        signIn(alice);

        DocumentContext userUpdated = awaitEvent("user.updated", data("userId", alice.id()));
        assertThat(userUpdated.read("$.eventId", String.class)).isNotBlank();
        assertThat(userUpdated.read("$.occurredAt", String.class)).isNotBlank();
        assertThat(userUpdated.read("$.producer", String.class)).isEqualTo("pawtrack-identity");
        assertThat(userUpdated.read("$.data.email", String.class)).isEqualTo(alice.email());
        assertThat(userUpdated.read("$.data.displayName", String.class)).isEqualTo("Alice");
        assertThat(userUpdated.read("$.data.timezone", String.class)).isEqualTo("Europe/Sarajevo");
        assertThat(userUpdated.read("$.data.emailNotifications", Boolean.class)).isTrue();
        assertThat(userUpdated.read("$.data", Map.class)).doesNotContainKey("type");

        DocumentContext memberAdded = awaitEvent("household.member.added", data("userId", alice.id()));
        assertThat(memberAdded.read("$.data.householdId", String.class)).isNotBlank();
        assertThat(memberAdded.read("$.data.role", String.class)).isEqualTo("OWNER");
    }

    @Test
    void getMe_secondCall_doesNotProvisionAgain() throws Exception {
        TestUser alice = TestUser.random("Alice");

        signIn(alice);
        signIn(alice);

        mvc.perform(get("/api/v1/me/households").with(alice.jwt()))
            .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void firstCalls_inParallel_provisionTheUserOnce() throws Exception {
        TestUser alice = TestUser.random("Alice");
        int parallel = 8;
        ExecutorService pool = Executors.newFixedThreadPool(parallel);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> statuses = new ArrayList<>();
            for (int i = 0; i < parallel; i++) {
                String path = i % 2 == 0 ? "/api/v1/me" : "/api/v1/me/households";
                statuses.add(pool.submit(() -> {
                    start.await();
                    return mvc.perform(get(path).with(alice.jwt())).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            for (Future<Integer> status : statuses) {
                assertThat(status.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            }
        } finally {
            pool.shutdownNow();
        }

        mvc.perform(get("/api/v1/me/households").with(alice.jwt()))
            .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void getMe_withEmailOfAnotherAccount_returns409() throws Exception {
        TestUser alice = TestUser.random("Alice");
        TestUser impostor = TestUser.random("Impostor").withEmail(alice.email());
        signIn(alice);

        mvc.perform(get("/api/v1/me").with(impostor.jwt()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void getMe_withoutNameClaim_usesPreferredUsername() throws Exception {
        TestUser bob = TestUser.random(null);

        mvc.perform(get("/api/v1/me").with(bob.jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.displayName").value(bob.username()));
    }

    @Test
    void getMe_withoutEmailClaim_returns403() throws Exception {
        TestUser noEmail = TestUser.random("No Email").withEmail(null);

        mvc.perform(get("/api/v1/me").with(noEmail.jwt()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403));
    }

    // ---- PATCH /api/v1/me ------------------------------------------------------------------------------------

    @Test
    void updateMe_withoutToken_returns401() throws Exception {
        mvc.perform(patch("/api/v1/me").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void updateMe_changesGivenFieldsAndPublishesUserUpdated() throws Exception {
        TestUser alice = TestUser.random("Alice");

        mvc.perform(patch("/api/v1/me").with(alice.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"displayName":"Alice B.","timezone":"Europe/Berlin","emailNotifications":false}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.displayName").value("Alice B."))
            .andExpect(jsonPath("$.timezone").value("Europe/Berlin"))
            .andExpect(jsonPath("$.emailNotifications").value(false));

        DocumentContext event = awaitEvent("user.updated", data("displayName", "Alice B."));
        assertThat(event.read("$.data.userId", String.class)).isEqualTo(alice.id().toString());
        assertThat(event.read("$.data.timezone", String.class)).isEqualTo("Europe/Berlin");
        assertThat(event.read("$.data.emailNotifications", Boolean.class)).isFalse();
    }

    @Test
    void updateMe_leavesOmittedFieldsUnchanged() throws Exception {
        TestUser alice = TestUser.random("Alice");
        signIn(alice);

        mvc.perform(patch("/api/v1/me").with(alice.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"timezone\":\"America/New_York\"}"))
            .andExpect(status().isOk());

        mvc.perform(get("/api/v1/me").with(alice.jwt()))
            .andExpect(jsonPath("$.displayName").value("Alice"))
            .andExpect(jsonPath("$.timezone").value("America/New_York"))
            .andExpect(jsonPath("$.emailNotifications").value(true));
    }

    @Test
    void updateMe_withUnknownTimezone_returns400() throws Exception {
        TestUser alice = TestUser.random("Alice");

        mvc.perform(patch("/api/v1/me").with(alice.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"timezone\":\"Mars/Olympus_Mons\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void updateMe_withEmptyDisplayName_returns400() throws Exception {
        TestUser alice = TestUser.random("Alice");

        mvc.perform(patch("/api/v1/me").with(alice.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void updateMe_withTooLongDisplayName_returns400() throws Exception {
        TestUser alice = TestUser.random("Alice");

        mvc.perform(patch("/api/v1/me").with(alice.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"%s\"}".formatted("x".repeat(101))))
            .andExpect(status().isBadRequest());
    }

    // ---- GET /api/v1/me/households ---------------------------------------------------------------------------

    @Test
    void getMyHouseholds_withoutToken_returns401() throws Exception {
        mvc.perform(get("/api/v1/me/households")).andExpect(status().isUnauthorized());
    }

    @Test
    void getMyHouseholds_listsOwnedAndJoinedHouseholdsWithRoles() throws Exception {
        TestUser owner = TestUser.random("Owner");
        TestUser sitter = TestUser.random("Sitter");
        String householdId = createHousehold(owner, "Beach house");
        join(owner, householdId, sitter, "SITTER");

        mvc.perform(get("/api/v1/me/households").with(sitter.jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[?(@.householdName == 'My pets')].role").value(contains("OWNER")))
            .andExpect(jsonPath("$[?(@.householdId == '%s')].householdName", householdId).value(contains("Beach house")))
            .andExpect(jsonPath("$[?(@.householdId == '%s')].role", householdId).value(contains("SITTER")));
    }
}
