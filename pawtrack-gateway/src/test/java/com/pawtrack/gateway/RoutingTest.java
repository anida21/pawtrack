package com.pawtrack.gateway;

import com.pawtrack.gateway.support.IntegrationTest;
import com.pawtrack.gateway.support.StubBackend;
import com.pawtrack.gateway.support.StubBackend.ReceivedRequest;
import com.pawtrack.gateway.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoutingTest extends IntegrationTest {

    private static final String PET_ID = "6f1c2a0e-3b7d-4c55-9a1e-2d8f0b4e7c11";

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "/api/v1/me,                                                    identity",
        "/api/v1/me/households,                                         identity",
        "/api/v1/households,                                            identity",
        "/api/v1/households/h-1,                                        identity",
        "/api/v1/households/h-1/members/u-1,                            identity",
        "/api/v1/households/h-1/invitations,                            identity",
        "/api/v1/invitations/abc123/accept,                             identity",
        "/api/v1/pets,                                                  pet",
        "/api/v1/pets/" + PET_ID + ",                                   pet",
        "/api/v1/pets/" + PET_ID + "/photo,                             pet",
        "/api/v1/pets/" + PET_ID + "/vaccinations,                      health",
        "/api/v1/pets/" + PET_ID + "/vaccinations/v-1,                  health",
        "/api/v1/pets/" + PET_ID + "/vet-visits,                        health",
        "/api/v1/pets/" + PET_ID + "/vet-visits/v-1,                    health",
        "/api/v1/pets/" + PET_ID + "/medications,                       health",
        "/api/v1/pets/" + PET_ID + "/medications/m-1,                   health",
        "/api/v1/pets/" + PET_ID + "/weights,                           health",
        "/api/v1/pets/" + PET_ID + "/summary,                           health",
        "/api/v1/reminders,                                             notification",
        "/api/v1/reminders/r-1,                                         notification",
        "/api/v1/reminders/r-1/done,                                    notification",
    })
    void routesEveryContractPathToItsService(String path, String service) throws Exception {
        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.valid())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.service").value(service))
            .andExpect(jsonPath("$.uri").value(path));

        StubBackend target = backend(service);
        assertThat(target.nextRequest().uri()).isEqualTo(path);
        BACKENDS.stream().filter(b -> b != target)
            .forEach(other -> assertThat(other.receivedNothing()).as(other.name() + " got no request").isTrue());
    }

    @Test
    void relaysTheTokenUnchanged() throws Exception {
        String token = TestTokens.valid();

        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token)))
            .andExpect(status().isOk());

        assertThat(IDENTITY.nextRequest().header(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + token);
    }

    @Test
    void keepsQueryStringMethodAndBody() throws Exception {
        mvc.perform(get("/api/v1/pets").queryParam("householdId", "h-1").queryParam("size", "5")
                .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.valid())))
            .andExpect(status().isOk());
        assertThat(PET.nextRequest().uri()).isEqualTo("/api/v1/pets?householdId=h-1&size=5");

        String body = "{\"name\":\"Miso\",\"species\":\"CAT\"}";
        mvc.perform(post("/api/v1/pets").contentType(MediaType.APPLICATION_JSON).content(body)
                .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.valid())))
            .andExpect(status().isCreated())
            .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/pets/created-1"));
        ReceivedRequest created = PET.nextRequest();
        assertThat(created.method()).isEqualTo("POST");
        assertThat(created.body()).isEqualTo(body);
        assertThat(created.header(HttpHeaders.CONTENT_TYPE)).startsWith(MediaType.APPLICATION_JSON_VALUE);

        mvc.perform(patch("/api/v1/me").contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"A\"}")
                .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.valid())))
            .andExpect(status().isOk());
        assertThat(IDENTITY.nextRequest().method()).isEqualTo("PATCH");

        mvc.perform(delete("/api/v1/reminders/r-1")
                .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.valid())))
            .andExpect(status().isOk());
        assertThat(NOTIFICATION.nextRequest().method()).isEqualTo("DELETE");
    }

    @Test
    void unknownPathIs404AndReachesNoService() throws Exception {
        mvc.perform(get("/api/v1/unknown").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.valid())))
            .andExpect(status().isNotFound());

        BACKENDS.forEach(b -> assertThat(b.receivedNothing()).isTrue());
    }

    @ParameterizedTest
    @CsvSource({"identity", "pet", "health", "notification"})
    void serviceApiDocsArePublic(String service) throws Exception {
        mvc.perform(get("/api-docs/" + service))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.service").value(service));

        assertThat(backend(service).nextRequest().uri()).isEqualTo("/v3/api-docs");
    }
}
