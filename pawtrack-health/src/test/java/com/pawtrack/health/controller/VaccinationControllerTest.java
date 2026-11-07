package com.pawtrack.health.controller;

import java.time.LocalDate;
import java.util.UUID;

import com.jayway.jsonpath.DocumentContext;
import com.pawtrack.health.repository.PetCopyRepository;
import com.pawtrack.health.support.Bodies;
import com.pawtrack.health.support.IntegrationTest;
import com.pawtrack.health.support.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VaccinationControllerTest extends IntegrationTest {

    @Autowired
    PetCopyRepository petCopies;

    private final TestUser owner = TestUser.random("Owner");

    @Test
    void createsVaccinationAndPublishesVaccinationRecorded() throws Exception {
        UUID petId = pets.create(owner, "Luna");
        String body = """
            {"vaccine":"Rabies","administeredOn":"2026-03-01","nextDueDate":"2027-03-01","batchNumber":"RB-2231",
             "clinic":"Vet Centar Sarajevo","notes":"No reaction"}""";

        String id = com.jayway.jsonpath.JsonPath.read(postJson(owner, path(petId, "vaccinations"), body)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id", notNullValue()))
            .andExpect(jsonPath("$.petId").value(petId.toString()))
            .andExpect(jsonPath("$.vaccine").value("Rabies"))
            .andExpect(jsonPath("$.administeredOn").value("2026-03-01"))
            .andExpect(jsonPath("$.nextDueDate").value("2027-03-01"))
            .andExpect(jsonPath("$.batchNumber").value("RB-2231"))
            .andExpect(jsonPath("$.clinic").value("Vet Centar Sarajevo"))
            .andExpect(jsonPath("$.notes").value("No reaction"))
            .andExpect(jsonPath("$.createdAt", notNullValue()))
            .andReturn().getResponse().getContentAsString(), "$.id");

        DocumentContext event = awaitEvent("health.vaccination.recorded", data("vaccinationId", id));
        assertThat(event.read("$.producer", String.class)).isEqualTo("pawtrack-health");
        assertThat(event.read("$.eventId", String.class)).isNotBlank();
        assertThat(event.read("$.occurredAt", String.class)).isNotBlank();
        assertThat(event.read("$.data.petId", String.class)).isEqualTo(petId.toString());
        assertThat(event.read("$.data.householdId", String.class)).isEqualTo(pets.householdOf(petId).toString());
        assertThat(event.read("$.data.vaccine", String.class)).isEqualTo("Rabies");
        assertThat(event.read("$.data.administeredOn", String.class)).isEqualTo("2026-03-01");
        assertThat(event.read("$.data.nextDueDate", String.class)).isEqualTo("2027-03-01");
    }

    @Test
    void eventLeavesOutNextDueDateWhenNotSet() throws Exception {
        UUID petId = pets.create(owner, "Luna");
        String id = create(owner, path(petId, "vaccinations"), """
            {"vaccine":"Leptospirosis","administeredOn":"2026-03-01"}""");

        DocumentContext event = awaitEvent("health.vaccination.recorded", data("vaccinationId", id));
        assertThat(event.read("$.data", java.util.Map.class)).doesNotContainKey("nextDueDate");
    }

    @Test
    void anyoneWhoMaySeeThePetMayRecordVaccinations() throws Exception {
        // health.yaml: read and write use the same rule; a SITTER is let through by pet's GET.
        UUID petId = pets.create(owner, "Luna");
        TestUser sitter = TestUser.random("Sitter");
        pets.grant(sitter, petId);

        create(sitter, path(petId, "vaccinations"), Bodies.VACCINATION);
    }

    @Test
    void writesRefreshTheLocalPetCopy() throws Exception {
        UUID petId = pets.create(owner, "Luna");
        UUID newHousehold = UUID.randomUUID();
        pets.move(petId, newHousehold, "Luna Maria");

        String id = create(owner, path(petId, "vaccinations"), Bodies.VACCINATION);

        assertThat(petCopies.findById(petId)).hasValueSatisfying(copy -> {
            assertThat(copy.getHouseholdId()).isEqualTo(newHousehold);
            assertThat(copy.getName()).isEqualTo("Luna Maria");
        });
        DocumentContext event = awaitEvent("health.vaccination.recorded", data("vaccinationId", id));
        assertThat(event.read("$.data.householdId", String.class)).isEqualTo(newHousehold.toString());
    }

    @Test
    void listsNewestFirstAndOnlyThisPet() throws Exception {
        UUID petId = pets.create(owner, "Luna");
        UUID otherPet = pets.create(owner, "Momo");
        create(owner, path(petId, "vaccinations"), """
            {"vaccine":"Parvo","administeredOn":"2025-05-01"}""");
        create(owner, path(petId, "vaccinations"), """
            {"vaccine":"Rabies","administeredOn":"2026-05-01"}""");
        create(owner, path(otherPet, "vaccinations"), """
            {"vaccine":"Other","administeredOn":"2026-06-01"}""");

        mvc.perform(get(path(petId, "vaccinations")).with(owner.jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].vaccine").value("Rabies"))
            .andExpect(jsonPath("$[1].vaccine").value("Parvo"));
    }

    @Test
    void emptyListForAPetWithoutVaccinations() throws Exception {
        UUID petId = pets.create(owner, "Luna");
        mvc.perform(get(path(petId, "vaccinations")).with(owner.jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void updateReplacesAllEditableFields() throws Exception {
        UUID petId = pets.create(owner, "Luna");
        String id = create(owner, path(petId, "vaccinations"), """
            {"vaccine":"Rabies","administeredOn":"2026-03-01","nextDueDate":"2027-03-01","clinic":"Old"}""");

        putJson(owner, path(petId, "vaccinations") + "/" + id, """
            {"vaccine":"Rabies (3y)","administeredOn":"2026-03-02","notes":"Corrected"}""")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.vaccine").value("Rabies (3y)"))
            .andExpect(jsonPath("$.administeredOn").value("2026-03-02"))
            .andExpect(jsonPath("$.nextDueDate").doesNotExist())
            .andExpect(jsonPath("$.clinic").doesNotExist())
            .andExpect(jsonPath("$.notes").value("Corrected"));
    }

    @Test
    void updatePublishesVaccinationRecordedAgain() throws Exception {
        UUID petId = pets.create(owner, "Luna");
        String id = create(owner, path(petId, "vaccinations"), """
            {"vaccine":"Rabies","administeredOn":"2026-03-01","nextDueDate":"2027-03-01"}""");
        awaitEvent("health.vaccination.recorded", data("vaccinationId", id));

        putJson(owner, path(petId, "vaccinations") + "/" + id, """
            {"vaccine":"Rabies","administeredOn":"2026-03-01","nextDueDate":"2029-03-01"}""")
            .andExpect(status().isOk());
        DocumentContext moved = awaitEvent("health.vaccination.recorded", data("vaccinationId", id));
        assertThat(moved.read("$.data.nextDueDate", String.class)).isEqualTo("2029-03-01");
        assertThat(moved.read("$.data.householdId", String.class)).isEqualTo(pets.householdOf(petId).toString());

        // Removing the due date is sent too (without nextDueDate), so notification can cancel the reminders.
        putJson(owner, path(petId, "vaccinations") + "/" + id, """
            {"vaccine":"Rabies","administeredOn":"2026-03-01"}""")
            .andExpect(status().isOk());
        DocumentContext cleared = awaitEvent("health.vaccination.recorded", data("vaccinationId", id));
        assertThat(cleared.read("$.data", java.util.Map.class)).doesNotContainKey("nextDueDate");
    }

    @Test
    void deleteRemovesTheVaccination() throws Exception {
        UUID petId = pets.create(owner, "Luna");
        String id = create(owner, path(petId, "vaccinations"), Bodies.VACCINATION);

        mvc.perform(delete(path(petId, "vaccinations") + "/" + id).with(owner.jwt()))
            .andExpect(status().isNoContent());

        mvc.perform(get(path(petId, "vaccinations")).with(owner.jwt()))
            .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(delete(path(petId, "vaccinations") + "/" + id).with(owner.jwt()))
            .andExpect(status().isNotFound());
        putJson(owner, path(petId, "vaccinations") + "/" + id, Bodies.VACCINATION)
            .andExpect(status().isNotFound());
    }

    @Test
    void unknownOrOtherPetsRecordIs404() throws Exception {
        UUID petId = pets.create(owner, "Luna");
        UUID otherPet = pets.create(owner, "Momo");
        String othersId = create(owner, path(otherPet, "vaccinations"), Bodies.VACCINATION);

        for (String id : new String[] {UUID.randomUUID().toString(), othersId}) {
            putJson(owner, path(petId, "vaccinations") + "/" + id, Bodies.VACCINATION)
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
            mvc.perform(delete(path(petId, "vaccinations") + "/" + id).with(owner.jwt()))
                .andExpect(status().isNotFound());
        }
    }

    @Test
    void invalidBodiesGet400() throws Exception {
        UUID petId = pets.create(owner, "Luna");
        String id = create(owner, path(petId, "vaccinations"), Bodies.VACCINATION);
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        String[] invalid = {
            "{\"administeredOn\":\"2026-03-01\"}",
            "{\"vaccine\":\" \",\"administeredOn\":\"2026-03-01\"}",
            "{\"vaccine\":\"%s\",\"administeredOn\":\"2026-03-01\"}".formatted("v".repeat(101)),
            "{\"vaccine\":\"Rabies\"}",
            "{\"vaccine\":\"Rabies\",\"administeredOn\":\"%s\"}".formatted(tomorrow),
            "{\"vaccine\":\"Rabies\",\"administeredOn\":\"2026-03-01\",\"nextDueDate\":\"2026-02-28\"}",
            "{\"vaccine\":\"Rabies\",\"administeredOn\":\"01.03.2026\"}",
            "{\"vaccine\":\"Rabies\",\"administeredOn\":\"2026-03-01\",\"batchNumber\":\"%s\"}".formatted("b".repeat(51)),
            "{\"vaccine\":\"Rabies\",\"administeredOn\":\"2026-03-01\",\"clinic\":\"%s\"}".formatted("c".repeat(121)),
            "{\"vaccine\":\"Rabies\",\"administeredOn\":\"2026-03-01\",\"notes\":\"%s\"}".formatted("n".repeat(2001)),
            "not json",
        };
        for (String body : invalid) {
            postJson(owner, path(petId, "vaccinations"), body)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
            putJson(owner, path(petId, "vaccinations") + "/" + id, body)
                .andExpect(status().isBadRequest());
        }
    }
}
