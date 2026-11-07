package com.pawtrack.gateway;

import com.pawtrack.gateway.support.IntegrationTest;
import com.pawtrack.gateway.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CorsTest extends IntegrationTest {

    private static final String UI = "http://localhost:4200";

    @Test
    void preflightFromTheUiIsAllowedWithoutToken() throws Exception {
        mvc.perform(options("/api/v1/pets")
                .header(HttpHeaders.ORIGIN, UI)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, UI))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                org.hamcrest.Matchers.containsString("POST")))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                org.hamcrest.Matchers.containsStringIgnoringCase("authorization")))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600"))
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));

        // Answered by the gateway itself.
        BACKENDS.forEach(b -> assertThat(b.receivedNothing()).isTrue());
    }

    @Test
    void preflightFromAnUnknownOriginIsRejected() throws Exception {
        mvc.perform(options("/api/v1/pets")
                .header(HttpHeaders.ORIGIN, "https://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
            .andExpect(status().isForbidden())
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void actualRequestFromTheUiCarriesCorsHeaders() throws Exception {
        mvc.perform(get("/api/v1/pets")
                .header(HttpHeaders.ORIGIN, UI)
                .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.valid())))
            .andExpect(status().isOk())
            .andExpect(header().stringValues(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, UI))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Location"));
    }

    @Test
    void unauthenticatedRequestFromTheUiStillCarriesCorsHeaders() throws Exception {
        // Lets the browser show the 401 to the app instead of a CORS error.
        mvc.perform(get("/api/v1/pets").header(HttpHeaders.ORIGIN, UI))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, UI));
    }
}
