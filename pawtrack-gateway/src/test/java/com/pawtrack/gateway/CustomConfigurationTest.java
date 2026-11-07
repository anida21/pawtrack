package com.pawtrack.gateway;

import com.pawtrack.gateway.support.TestJwtDecoderConfig;
import com.pawtrack.gateway.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Env-var driven configuration: allowed origins and an unreachable service (nothing listens on port 1). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtDecoderConfig.class)
@TestPropertySource(properties = {
    "CORS_ALLOWED_ORIGINS=https://app.pawtrack.test,https://admin.pawtrack.test",
    "PET_URL=http://127.0.0.1:1",
})
class CustomConfigurationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void allowedOriginsComeFromTheEnvironment() throws Exception {
        for (String origin : new String[] {"https://app.pawtrack.test", "https://admin.pawtrack.test"}) {
            mvc.perform(options("/api/v1/pets")
                    .header(HttpHeaders.ORIGIN, origin)
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin));
        }
        mvc.perform(options("/api/v1/pets")
                .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
            .andExpect(status().isForbidden());
    }

    @Test
    void unreachableServiceIs503ProblemDetail() throws Exception {
        mvc.perform(get("/api/v1/pets").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.valid())))
            .andExpect(status().isServiceUnavailable())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/problem+json"))
            .andExpect(jsonPath("$.title").value("Service unavailable"))
            .andExpect(jsonPath("$.status").value(503));
    }
}
