package com.pawtrack.gateway;

import com.pawtrack.gateway.support.IntegrationTest;
import com.pawtrack.gateway.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityTest extends IntegrationTest {

    @Test
    void requestWithoutTokenIs401AndNeverReachesAService() throws Exception {
        mvc.perform(get("/api/v1/pets"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, org.hamcrest.Matchers.startsWith("Bearer")));
        mvc.perform(post("/api/v1/households").content("{}"))
            .andExpect(status().isUnauthorized());

        BACKENDS.forEach(b -> assertThat(b.receivedNothing()).isTrue());
    }

    @Test
    void expiredTokenIs401() throws Exception {
        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.expired())))
            .andExpect(status().isUnauthorized());
        assertThat(IDENTITY.receivedNothing()).isTrue();
    }

    @Test
    void tokenWithForeignSignatureIs401() throws Exception {
        mvc.perform(get("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.signedByUnknownKey())))
            .andExpect(status().isUnauthorized());
        assertThat(IDENTITY.receivedNothing()).isTrue();
    }

    @Test
    void tokenFromAnotherIssuerIs401() throws Exception {
        mvc.perform(get("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.fromOtherIssuer())))
            .andExpect(status().isUnauthorized());
        assertThat(IDENTITY.receivedNothing()).isTrue();
    }

    @Test
    void garbageTokenIs401() throws Exception {
        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void healthAndSwaggerUiArePublic() throws Exception {
        mvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs/swagger-config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.urls[*].url").value(org.hamcrest.Matchers.containsInAnyOrder(
                "/api-docs/identity", "/api-docs/pet", "/api-docs/health", "/api-docs/notification")));
    }

    @Test
    void otherActuatorEndpointsNeedAToken() throws Exception {
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
    }
}
