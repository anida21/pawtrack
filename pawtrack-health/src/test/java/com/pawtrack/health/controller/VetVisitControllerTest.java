package com.pawtrack.health.controller;

import java.util.UUID;

import com.pawtrack.health.support.Bodies;
import com.pawtrack.health.support.IntegrationTest;
import com.pawtrack.health.support.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VetVisitControllerTest extends IntegrationTest {

    private final TestUser owner = TestUser.random("Owner");

    @Test
    void createsVetVisit() throws Exception {
        UUID petId = pets.create(owner, "Luna");
        postJson(owner, path(petId, "vet-visits"), """
            {"visitDate":"2026-10-02T08:15:00Z","clinic":"Vet Centar","vetName":"Dr. Hodzic","reason":"Limping",
             "diagnosis":"Sprain","treatment":"Rest for a week","cost":45.5,"currency":"EUR"}""")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id", notNullValue()))
            .andExpect(jsonPath("$.petId").value(petId.toString()))
            .andExpect(jsonPath("$.visitDate").value("2026-10-02T08:15:00Z"))
            .andExpect(jsonPath("$.clinic").value("Vet Centar"))
            .andExpect(jsonPath("$.vetName").value("Dr. Hodzic"))
            .andExpect(jsonPath("$.reason").value("Limping"))
            .andExpect(jsonPath("$.diagnosis").value("Sprain"))
            .andExpect(jsonPath("$.treatment").value("Rest for a week"))
            .andExpect(jsonPath("$.cost").value(45.5))
            .andExpect(jsonPath("$.currency").value("EUR"))
            .andExpect(jsonPath("$.createdAt", notNullValue()));
    }

    @Test
    void currencyDefaultsToBamAndCostIsRoundedToCents() throws Exception {
        UUID petId = pets.create(owner, "Luna");
        postJson(owner, path(petId, "vet-visits"), """
            {"visitDate":"2026-10-02T10:15:00+02:00","reason":"Checkup","cost":30.125}""")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.visitDate").value("2026-10-02T08:15:00Z"))
            .andExpect(jsonPath("$.currency").value("BAM"))
            .andExpect(jsonPath("$.cost").value(30.13));
    }

    @Test
    void listsNewestFirst() throws Exception {
        UUID petId = pets.create(owner, "Luna");
        create(owner, path(petId, "vet-visits"), """
            {"visitDate":"2025-01-02T08:00:00Z","reason":"First"}""");
        create(owner, path(petId, "vet-visits"), """
            {"visitDate":"2026-01-02T08:00:00Z","reason":"Second"}""");

        mvc.perform(get(path(petId, "vet-visits")).with(owner.jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].reason").value("Second"))
            .andExpect(jsonPath("$[1].reason").value("First"));
    }

    @Test
    void updateReplacesAllEditableFields() throws Exception {
        UUID petId = pets.create(owner, "Luna");
        String id = create(owner, path(petId, "vet-visits"), """
            {"visitDate":"2026-01-02T08:00:00Z","reason":"Checkup","cost":20,"currency":"EUR","vetName":"Dr. A"}""");

        putJson(owner, path(petId, "vet-visits") + "/" + id, """
            {"visitDate":"2026-01-03T08:00:00Z","reason":"Vaccination"}""")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.visitDate").value("2026-01-03T08:00:00Z"))
            .andExpect(jsonPath("$.reason").value("Vaccination"))
            .andExpect(jsonPath("$.vetName").doesNotExist())
            .andExpect(jsonPath("$.cost").doesNotExist())
            .andExpect(jsonPath("$.currency").value("BAM"));
    }

    @Test
    void deleteRemovesTheVisit() throws Exception {
        UUID petId = pets.create(owner, "Luna");
        String id = create(owner, path(petId, "vet-visits"), Bodies.VET_VISIT);

        mvc.perform(delete(path(petId, "vet-visits") + "/" + id).with(owner.jwt()))
            .andExpect(status().isNoContent());

        mvc.perform(get(path(petId, "vet-visits")).with(owner.jwt()))
            .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(delete(path(petId, "vet-visits") + "/" + id).with(owner.jwt()))
            .andExpect(status().isNotFound());
    }

    @Test
    void unknownOrOtherPetsRecordIs404() throws Exception {
        UUID petId = pets.create(owner, "Luna");
        UUID otherPet = pets.create(owner, "Momo");
        String othersId = create(owner, path(otherPet, "vet-visits"), Bodies.VET_VISIT);

        for (String id : new String[] {UUID.randomUUID().toString(), othersId}) {
            putJson(owner, path(petId, "vet-visits") + "/" + id, Bodies.VET_VISIT)
                .andExpect(status().isNotFound());
            mvc.perform(delete(path(petId, "vet-visits") + "/" + id).with(owner.jwt()))
                .andExpect(status().isNotFound());
        }
    }

    @Test
    void invalidBodiesGet400() throws Exception {
        UUID petId = pets.create(owner, "Luna");
        String id = create(owner, path(petId, "vet-visits"), Bodies.VET_VISIT);
        String[] invalid = {
            "{\"reason\":\"Checkup\"}",
            "{\"visitDate\":\"2026-01-02T08:00:00Z\"}",
            "{\"visitDate\":\"2026-01-02T08:00:00Z\",\"reason\":\"\"}",
            "{\"visitDate\":\"2026-01-02T08:00:00Z\",\"reason\":\"%s\"}".formatted("r".repeat(201)),
            "{\"visitDate\":\"yesterday\",\"reason\":\"Checkup\"}",
            "{\"visitDate\":\"2026-01-02T08:00:00Z\",\"reason\":\"Checkup\",\"cost\":-1}",
            "{\"visitDate\":\"2026-01-02T08:00:00Z\",\"reason\":\"Checkup\",\"cost\":1000000001}",
            "{\"visitDate\":\"2026-01-02T08:00:00Z\",\"reason\":\"Checkup\",\"currency\":\"eur\"}",
            "{\"visitDate\":\"2026-01-02T08:00:00Z\",\"reason\":\"Checkup\",\"currency\":\"EURO\"}",
            "{\"visitDate\":\"2026-01-02T08:00:00Z\",\"reason\":\"Checkup\",\"clinic\":\"%s\"}".formatted("c".repeat(121)),
            "{\"visitDate\":\"2026-01-02T08:00:00Z\",\"reason\":\"Checkup\",\"vetName\":\"%s\"}".formatted("v".repeat(101)),
            "{\"visitDate\":\"2026-01-02T08:00:00Z\",\"reason\":\"Checkup\",\"diagnosis\":\"%s\"}".formatted("d".repeat(2001)),
            "{\"visitDate\":\"2026-01-02T08:00:00Z\",\"reason\":\"Checkup\",\"treatment\":\"%s\"}".formatted("t".repeat(2001)),
        };
        for (String body : invalid) {
            postJson(owner, path(petId, "vet-visits"), body)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
            putJson(owner, path(petId, "vet-visits") + "/" + id, body)
                .andExpect(status().isBadRequest());
        }
    }
}
