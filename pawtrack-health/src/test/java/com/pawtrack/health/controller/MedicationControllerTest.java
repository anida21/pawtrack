package com.pawtrack.health.controller;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.pawtrack.health.support.Bodies;
import com.pawtrack.health.support.IntegrationTest;
import com.pawtrack.health.support.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MedicationControllerTest extends IntegrationTest {

    private final TestUser owner = TestUser.random("Owner");
    private final LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);

    @Test
    void createsMedicationAndPublishesMedicationScheduled() throws Exception {
        UUID petId = pets.create(owner, "Luna");
        LocalDate start = today.minusDays(2);
        LocalDate end = today.plusDays(10);
        String body = """
            {"name":"Apoquel","dosage":"1 tablet","startDate":"%s","endDate":"%s","frequency":"TWICE_DAILY",
             "timeOfDay":"08:00","notes":"With food"}""".formatted(start, end);

        String id = JsonPath.read(postJson(owner, path(petId, "medications"), body)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id", notNullValue()))
            .andExpect(jsonPath("$.petId").value(petId.toString()))
            .andExpect(jsonPath("$.name").value("Apoquel"))
            .andExpect(jsonPath("$.dosage").value("1 tablet"))
            .andExpect(jsonPath("$.startDate").value(start.toString()))
            .andExpect(jsonPath("$.endDate").value(end.toString()))
            .andExpect(jsonPath("$.frequency").value("TWICE_DAILY"))
            .andExpect(jsonPath("$.timeOfDay").value("08:00"))
            .andExpect(jsonPath("$.notes").value("With food"))
            .andExpect(jsonPath("$.active").value(true))
            .andReturn().getResponse().getContentAsString(), "$.id");

        DocumentContext event = awaitEvent("health.medication.scheduled", data("medicationId", id));
        assertThat(event.read("$.producer", String.class)).isEqualTo("pawtrack-health");
        assertThat(event.read("$.data.petId", String.class)).isEqualTo(petId.toString());
        assertThat(event.read("$.data.householdId", String.class)).isEqualTo(pets.householdOf(petId).toString());
        assertThat(event.read("$.data.name", String.class)).isEqualTo("Apoquel");
        assertThat(event.read("$.data.dosage", String.class)).isEqualTo("1 tablet");
        assertThat(event.read("$.data.startDate", String.class)).isEqualTo(start.toString());
        assertThat(event.read("$.data.endDate", String.class)).isEqualTo(end.toString());
        assertThat(event.read("$.data.frequency", String.class)).isEqualTo("TWICE_DAILY");
        assertThat(event.read("$.data.timeOfDay", String.class)).isEqualTo("08:00");
    }

    @Test
    void scheduledEventLeavesOutEmptyOptionalFields() throws Exception {
        UUID petId = pets.create(owner, "Luna");
        String id = create(owner, path(petId, "medications"), """
            {"name":"Drontal","dosage":"1 tablet","startDate":"%s","frequency":"MONTHLY"}""".formatted(today));

        DocumentContext event = awaitEvent("health.medication.scheduled", data("medicationId", id));
        assertThat(event.read("$.data", Map.class)).doesNotContainKeys("endDate", "timeOfDay");
    }

    @Test
    void updatePublishesMedicationScheduledAgain() throws Exception {
        UUID petId = pets.create(owner, "Luna");
        String id = create(owner, path(petId, "medications"), """
            {"name":"Apoquel","dosage":"1 tablet","startDate":"%s","frequency":"DAILY","timeOfDay":"08:00"}"""
            .formatted(today));
        awaitEvent("health.medication.scheduled", data("medicationId", id));

        putJson(owner, path(petId, "medications") + "/" + id, """
            {"name":"Apoquel","dosage":"half a tablet","startDate":"%s","frequency":"DAILY","timeOfDay":"19:30"}"""
            .formatted(today))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.dosage").value("half a tablet"))
            .andExpect(jsonPath("$.timeOfDay").value("19:30"));

        DocumentContext event = awaitEvent("health.medication.scheduled", data("medicationId", id));
        assertThat(event.read("$.data.dosage", String.class)).isEqualTo("half a tablet");
        assertThat(event.read("$.data.timeOfDay", String.class)).isEqualTo("19:30");
    }

    @Test
    void deletePublishesMedicationCancelled() throws Exception {
        UUID petId = pets.create(owner, "Luna");
        String id = create(owner, path(petId, "medications"), Bodies.MEDICATION);

        mvc.perform(delete(path(petId, "medications") + "/" + id).with(owner.jwt()))
            .andExpect(status().isNoContent());

        DocumentContext event = awaitEvent("health.medication.cancelled", data("medicationId", id));
        assertThat(event.read("$.data", Map.class)).containsOnlyKeys("medicationId", "petId");
        assertThat(event.read("$.data.petId", String.class)).isEqualTo(petId.toString());
        mvc.perform(get(path(petId, "medications")).with(owner.jwt()))
            .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(delete(path(petId, "medications") + "/" + id).with(owner.jwt()))
            .andExpect(status().isNotFound());
    }

    @Test
    void activeOnlyFiltersFinishedMedications() throws Exception {
        UUID petId = pets.create(owner, "Luna");
        String body = "{\"name\":\"%s\",\"dosage\":\"1\",\"startDate\":\"%s\",%s\"frequency\":\"%s\"}";
        create(owner, path(petId, "medications"),
            body.formatted("Ended", today.minusDays(20), "\"endDate\":\"%s\",".formatted(today.minusDays(1)), "DAILY"));
        create(owner, path(petId, "medications"),
            body.formatted("EndsToday", today.minusDays(5), "\"endDate\":\"%s\",".formatted(today), "DAILY"));
        create(owner, path(petId, "medications"), body.formatted("OpenEnded", today.minusDays(30), "", "MONTHLY"));
        create(owner, path(petId, "medications"), body.formatted("OnceDone", today.minusDays(3), "", "ONCE"));
        create(owner, path(petId, "medications"), body.formatted("OnceToday", today, "", "ONCE"));
        create(owner, path(petId, "medications"), body.formatted("Upcoming", today.plusDays(7), "", "WEEKLY"));

        mvc.perform(get(path(petId, "medications")).with(owner.jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(6))
            .andExpect(jsonPath("$[0].name").value("Upcoming"))
            .andExpect(jsonPath("$[?(@.name == 'Ended')].active").value(false))
            .andExpect(jsonPath("$[?(@.name == 'OnceDone')].active").value(false))
            .andExpect(jsonPath("$[?(@.name == 'OpenEnded')].active").value(true));

        mvc.perform(get(path(petId, "medications")).param("activeOnly", "true").with(owner.jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].name", containsInAnyOrder("EndsToday", "OpenEnded", "OnceToday", "Upcoming")));
    }

    @Test
    void malformedActiveOnlyGets400() throws Exception {
        UUID petId = pets.create(owner, "Luna");
        mvc.perform(get(path(petId, "medications")).param("activeOnly", "maybe").with(owner.jwt()))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void unknownOrOtherPetsRecordIs404() throws Exception {
        UUID petId = pets.create(owner, "Luna");
        UUID otherPet = pets.create(owner, "Momo");
        String othersId = create(owner, path(otherPet, "medications"), Bodies.MEDICATION);

        for (String id : new String[] {UUID.randomUUID().toString(), othersId}) {
            putJson(owner, path(petId, "medications") + "/" + id, Bodies.MEDICATION)
                .andExpect(status().isNotFound());
            mvc.perform(delete(path(petId, "medications") + "/" + id).with(owner.jwt()))
                .andExpect(status().isNotFound());
        }
    }

    @Test
    void invalidBodiesGet400() throws Exception {
        UUID petId = pets.create(owner, "Luna");
        String id = create(owner, path(petId, "medications"), Bodies.MEDICATION);
        String valid = "\"name\":\"Apoquel\",\"dosage\":\"1 tablet\",\"startDate\":\"2026-01-10\"";
        String[] invalid = {
            "{\"dosage\":\"1\",\"startDate\":\"2026-01-10\",\"frequency\":\"DAILY\"}",
            "{\"name\":\"A\",\"startDate\":\"2026-01-10\",\"frequency\":\"DAILY\"}",
            "{\"name\":\"A\",\"dosage\":\"1\",\"frequency\":\"DAILY\"}",
            "{%s}".formatted(valid),
            "{%s,\"frequency\":\"HOURLY\"}".formatted(valid),
            "{%s,\"frequency\":\"DAILY\",\"endDate\":\"2026-01-09\"}".formatted(valid),
            "{%s,\"frequency\":\"DAILY\",\"timeOfDay\":\"24:00\"}".formatted(valid),
            "{%s,\"frequency\":\"DAILY\",\"timeOfDay\":\"8:00\"}".formatted(valid),
            "{\"name\":\" \",\"dosage\":\"1\",\"startDate\":\"2026-01-10\",\"frequency\":\"DAILY\"}",
            "{\"name\":\"%s\",\"dosage\":\"1\",\"startDate\":\"2026-01-10\",\"frequency\":\"DAILY\"}".formatted("n".repeat(101)),
            "{\"name\":\"A\",\"dosage\":\"%s\",\"startDate\":\"2026-01-10\",\"frequency\":\"DAILY\"}".formatted("d".repeat(61)),
            "{%s,\"frequency\":\"DAILY\",\"notes\":\"%s\"}".formatted(valid, "n".repeat(2001)),
        };
        for (String body : invalid) {
            postJson(owner, path(petId, "medications"), body)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
            putJson(owner, path(petId, "medications") + "/" + id, body)
                .andExpect(status().isBadRequest());
        }
    }
}
