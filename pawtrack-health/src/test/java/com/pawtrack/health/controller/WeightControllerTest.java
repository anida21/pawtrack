package com.pawtrack.health.controller;

import java.time.LocalDate;
import java.util.UUID;

import com.pawtrack.health.support.IntegrationTest;
import com.pawtrack.health.support.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WeightControllerTest extends IntegrationTest {

    private final TestUser owner = TestUser.random("Owner");

    @Test
    void recordsWeight() throws Exception {
        UUID petId = pets.create(owner, "Luna");
        postJson(owner, path(petId, "weights"), """
            {"measuredOn":"2026-10-01","weightKg":12.3456}""")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id", notNullValue()))
            .andExpect(jsonPath("$.petId").value(petId.toString()))
            .andExpect(jsonPath("$.measuredOn").value("2026-10-01"))
            .andExpect(jsonPath("$.weightKg").value(12.346));
    }

    @Test
    void listsOldestFirstWithInclusiveDateFilter() throws Exception {
        UUID petId = pets.create(owner, "Luna");
        for (String day : new String[] {"2026-03-01", "2026-01-01", "2026-02-01", "2026-04-01"}) {
            create(owner, path(petId, "weights"), "{\"measuredOn\":\"%s\",\"weightKg\":10}".formatted(day));
        }

        mvc.perform(get(path(petId, "weights")).with(owner.jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].measuredOn", contains("2026-01-01", "2026-02-01", "2026-03-01", "2026-04-01")));
        mvc.perform(get(path(petId, "weights")).param("from", "2026-02-01").param("to", "2026-03-01")
                .with(owner.jwt()))
            .andExpect(jsonPath("$[*].measuredOn", contains("2026-02-01", "2026-03-01")));
        mvc.perform(get(path(petId, "weights")).param("from", "2026-03-15").with(owner.jwt()))
            .andExpect(jsonPath("$[*].measuredOn", contains("2026-04-01")));
        mvc.perform(get(path(petId, "weights")).param("to", "2026-01-31").with(owner.jwt()))
            .andExpect(jsonPath("$[*].measuredOn", contains("2026-01-01")));
    }

    @Test
    void invalidDateFiltersGet400() throws Exception {
        UUID petId = pets.create(owner, "Luna");
        mvc.perform(get(path(petId, "weights")).param("from", "2026-03-02").param("to", "2026-03-01")
                .with(owner.jwt()))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get(path(petId, "weights")).param("from", "March").with(owner.jwt()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void invalidBodiesGet400() throws Exception {
        UUID petId = pets.create(owner, "Luna");
        String[] invalid = {
            "{\"weightKg\":10}",
            "{\"measuredOn\":\"2026-01-01\"}",
            "{\"measuredOn\":\"2026-01-01\",\"weightKg\":0}",
            "{\"measuredOn\":\"2026-01-01\",\"weightKg\":-3}",
            "{\"measuredOn\":\"2026-01-01\",\"weightKg\":200.01}",
            "{\"measuredOn\":\"2026-01-01\",\"weightKg\":\"heavy\"}",
            "{\"measuredOn\":\"%s\",\"weightKg\":10}".formatted(LocalDate.now().plusDays(1)),
        };
        for (String body : invalid) {
            postJson(owner, path(petId, "weights"), body)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
    }

    @Test
    void boundaryWeightsAreAccepted() throws Exception {
        UUID petId = pets.create(owner, "Luna");
        create(owner, path(petId, "weights"), "{\"measuredOn\":\"2026-01-01\",\"weightKg\":0.01}");
        create(owner, path(petId, "weights"), "{\"measuredOn\":\"2026-01-01\",\"weightKg\":200}");
    }
}
