package com.pawtrack.pet.controller;

import java.time.LocalDate;
import java.util.UUID;

import com.jayway.jsonpath.DocumentContext;
import com.pawtrack.pet.dto.HouseholdRole;
import com.pawtrack.pet.support.IntegrationTest;
import com.pawtrack.pet.support.TestUser;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PetControllerTest extends IntegrationTest {

    private final TestUser owner = TestUser.random("Owner");
    private final TestUser stranger = TestUser.random("Stranger");

    @Nested
    class Create {

        @Test
        void createsPetAndPublishesPetCreated() throws Exception {
            UUID householdId = household(owner);
            LocalDate birthDate = LocalDate.now().minusMonths(26).minusDays(3);

            String body = """
                {"householdId":"%s","name":"Luna","species":"DOG","breed":"Border Collie","sex":"FEMALE",
                 "birthDate":"%s","neutered":true,"microchipNumber":"123456789012345","color":"black/white",
                 "notes":"Afraid of thunder"}""".formatted(householdId, birthDate);

            String id = com.jayway.jsonpath.JsonPath.read(mvc.perform(post("/api/v1/pets").with(owner.jwt())
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.householdId").value(householdId.toString()))
                .andExpect(jsonPath("$.name").value("Luna"))
                .andExpect(jsonPath("$.species").value("DOG"))
                .andExpect(jsonPath("$.breed").value("Border Collie"))
                .andExpect(jsonPath("$.sex").value("FEMALE"))
                .andExpect(jsonPath("$.birthDate").value(birthDate.toString()))
                .andExpect(jsonPath("$.neutered").value(true))
                .andExpect(jsonPath("$.microchipNumber").value("123456789012345"))
                .andExpect(jsonPath("$.color").value("black/white"))
                .andExpect(jsonPath("$.notes").value("Afraid of thunder"))
                .andExpect(jsonPath("$.ageInMonths").value(26))
                .andExpect(jsonPath("$.hasPhoto").value(false))
                .andExpect(jsonPath("$.createdBy").value(owner.id().toString()))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andExpect(jsonPath("$.updatedAt", nullValue()))
                .andReturn().getResponse().getContentAsString(), "$.id");

            DocumentContext event = awaitEvent("pet.created", data("petId", id));
            assertThat(event.read("$.producer", String.class)).isEqualTo("pawtrack-pet");
            assertThat(event.read("$.eventId", String.class)).isNotBlank();
            assertThat(event.read("$.occurredAt", String.class)).isNotBlank();
            assertThat(event.read("$.data.householdId", String.class)).isEqualTo(householdId.toString());
            assertThat(event.read("$.data.name", String.class)).isEqualTo("Luna");
            assertThat(event.read("$.data.species", String.class)).isEqualTo("DOG");
        }

        @Test
        void locationHeaderPointsToThePet() throws Exception {
            UUID householdId = household(owner);
            String location = mvc.perform(post("/api/v1/pets").with(owner.jwt())
                    .contentType(MediaType.APPLICATION_JSON).content(petJson(householdId, "Momo", "CAT")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

            mvc.perform(get(location).with(owner.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Momo"));
        }

        @Test
        void memberMayCreate() throws Exception {
            UUID householdId = household(owner);
            TestUser member = TestUser.random("Member");
            identity.grant(member, householdId, HouseholdRole.MEMBER);

            createPet(member, householdId, "Kiwi", "BIRD");
        }

        @Test
        void sitterMayNotCreate() throws Exception {
            UUID householdId = household(owner);
            TestUser sitter = TestUser.random("Sitter");
            identity.grant(sitter, householdId, HouseholdRole.SITTER);

            mvc.perform(post("/api/v1/pets").with(sitter.jwt())
                    .contentType(MediaType.APPLICATION_JSON).content(petJson(householdId, "Rex", "DOG")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        void nonMemberGets403() throws Exception {
            UUID householdId = household(owner);
            mvc.perform(post("/api/v1/pets").with(stranger.jwt())
                    .contentType(MediaType.APPLICATION_JSON).content(petJson(householdId, "Rex", "DOG")))
                .andExpect(status().isForbidden());
        }

        @Test
        void invalidBodiesGet400() throws Exception {
            UUID householdId = household(owner);
            String[] invalid = {
                "{\"householdId\":\"%s\",\"species\":\"DOG\"}".formatted(householdId),
                "{\"householdId\":\"%s\",\"name\":\" \",\"species\":\"DOG\"}".formatted(householdId),
                "{\"householdId\":\"%s\",\"name\":\"%s\",\"species\":\"DOG\"}".formatted(householdId, "x".repeat(61)),
                "{\"name\":\"Rex\",\"species\":\"DOG\"}",
                "{\"householdId\":\"%s\",\"name\":\"Rex\"}".formatted(householdId),
                "{\"householdId\":\"%s\",\"name\":\"Rex\",\"species\":\"DRAGON\"}".formatted(householdId),
                "{\"householdId\":\"%s\",\"name\":\"Rex\",\"species\":\"DOG\",\"microchipNumber\":\"12345\"}"
                    .formatted(householdId),
                "{\"householdId\":\"%s\",\"name\":\"Rex\",\"species\":\"DOG\",\"birthDate\":\"%s\"}"
                    .formatted(householdId, LocalDate.now().plusDays(1)),
                "{\"householdId\":\"%s\",\"name\":\"Rex\",\"species\":\"DOG\",\"notes\":\"%s\"}"
                    .formatted(householdId, "n".repeat(2001)),
            };
            for (String body : invalid) {
                mvc.perform(post("/api/v1/pets").with(owner.jwt())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
            }
        }

        @Test
        void requiresAuthentication() throws Exception {
            mvc.perform(post("/api/v1/pets")
                    .contentType(MediaType.APPLICATION_JSON).content(petJson(UUID.randomUUID(), "Rex", "DOG")))
                .andExpect(status().isUnauthorized());
        }

        @Test
        void identityDownGives503() throws Exception {
            UUID householdId = household(owner);
            identity.setDown(true);
            mvc.perform(post("/api/v1/pets").with(owner.jwt())
                    .contentType(MediaType.APPLICATION_JSON).content(petJson(householdId, "Rex", "DOG")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
    }

    @Nested
    class List {

        @Test
        void listsPetsOfAllHouseholdsOfTheCaller() throws Exception {
            UUID home = household(owner);
            UUID parents = UUID.randomUUID();
            identity.grant(owner, parents, HouseholdRole.SITTER);
            TestUser parent = TestUser.random("Parent");
            identity.grant(parent, parents, HouseholdRole.OWNER);
            UUID foreign = household(stranger);

            createPet(owner, home, "Luna", "DOG");
            createPet(owner, home, "Momo", "CAT");
            createPet(parent, parents, "Bruno", "DOG");
            createPet(stranger, foreign, "Hidden", "DOG");

            mvc.perform(get("/api/v1/pets").with(owner.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", containsInAnyOrder("Luna", "Momo", "Bruno")))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(1));
        }

        @Test
        void filtersByHouseholdAndSpecies() throws Exception {
            UUID home = household(owner);
            UUID second = household(owner);
            createPet(owner, home, "Luna", "DOG");
            createPet(owner, home, "Momo", "CAT");
            createPet(owner, second, "Bruno", "DOG");

            mvc.perform(get("/api/v1/pets").with(owner.jwt()).param("householdId", home.toString()))
                .andExpect(jsonPath("$.content[*].name", containsInAnyOrder("Luna", "Momo")));
            mvc.perform(get("/api/v1/pets").with(owner.jwt()).param("species", "DOG"))
                .andExpect(jsonPath("$.content[*].name", containsInAnyOrder("Luna", "Bruno")));
            mvc.perform(get("/api/v1/pets").with(owner.jwt())
                    .param("householdId", home.toString()).param("species", "DOG"))
                .andExpect(jsonPath("$.content[*].name", containsInAnyOrder("Luna")));
        }

        @Test
        void householdOfSomeoneElseMatchesNothing() throws Exception {
            UUID foreign = household(stranger);
            createPet(stranger, foreign, "Hidden", "DOG");
            household(owner);

            mvc.perform(get("/api/v1/pets").with(owner.jwt()).param("householdId", foreign.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        void callerWithoutHouseholdsGetsEmptyPage() throws Exception {
            mvc.perform(get("/api/v1/pets").with(TestUser.random("Nobody").jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        }

        @Test
        void pagesAreSortedByName() throws Exception {
            UUID home = household(owner);
            createPet(owner, home, "Charlie", "DOG");
            createPet(owner, home, "Alfie", "DOG");
            createPet(owner, home, "Bella", "DOG");

            mvc.perform(get("/api/v1/pets").with(owner.jwt()).param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Charlie"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        }

        @Test
        void deletedPetsAreNotListed() throws Exception {
            UUID home = household(owner);
            String id = createPet(owner, home, "Gone", "FISH");
            createPet(owner, home, "Stays", "FISH");
            mvc.perform(delete("/api/v1/pets/{id}", id).with(owner.jwt())).andExpect(status().isNoContent());

            mvc.perform(get("/api/v1/pets").with(owner.jwt()))
                .andExpect(jsonPath("$.content[*].name", containsInAnyOrder("Stays")));
        }

        @Test
        void invalidParametersGet400() throws Exception {
            String[][] invalid = {{"size", "101"}, {"size", "0"}, {"page", "-1"}, {"species", "DRAGON"},
                {"householdId", "not-a-uuid"}};
            for (String[] param : invalid) {
                mvc.perform(get("/api/v1/pets").with(owner.jwt()).param(param[0], param[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
            }
        }

        @Test
        void requiresAuthentication() throws Exception {
            mvc.perform(get("/api/v1/pets")).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class Get {

        @Test
        void everyHouseholdRoleMayRead() throws Exception {
            UUID home = household(owner);
            String id = createPet(owner, home, "Luna", "DOG");
            TestUser member = TestUser.random("Member");
            TestUser sitter = TestUser.random("Sitter");
            identity.grant(member, home, HouseholdRole.MEMBER);
            identity.grant(sitter, home, HouseholdRole.SITTER);

            for (TestUser reader : new TestUser[] {owner, member, sitter}) {
                mvc.perform(get("/api/v1/pets/{id}", id).with(reader.jwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.ageInMonths", nullValue()));
            }
        }

        @Test
        void nonMemberGets403() throws Exception {
            String id = createPet(owner, household(owner), "Luna", "DOG");
            mvc.perform(get("/api/v1/pets/{id}", id).with(stranger.jwt()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        void formerMemberLosesAccess() throws Exception {
            UUID home = household(owner);
            String id = createPet(owner, home, "Luna", "DOG");
            TestUser member = TestUser.random("Member");
            identity.grant(member, home, HouseholdRole.MEMBER);
            mvc.perform(get("/api/v1/pets/{id}", id).with(member.jwt())).andExpect(status().isOk());

            identity.revoke(member, home);
            mvc.perform(get("/api/v1/pets/{id}", id).with(member.jwt())).andExpect(status().isForbidden());
        }

        @Test
        void unknownOrDeletedPetGets404() throws Exception {
            mvc.perform(get("/api/v1/pets/{id}", UUID.randomUUID()).with(owner.jwt()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

            String id = createPet(owner, household(owner), "Gone", "DOG");
            mvc.perform(delete("/api/v1/pets/{id}", id).with(owner.jwt())).andExpect(status().isNoContent());
            mvc.perform(get("/api/v1/pets/{id}", id).with(owner.jwt())).andExpect(status().isNotFound());
        }

        @Test
        void malformedIdGets400() throws Exception {
            mvc.perform(get("/api/v1/pets/{id}", "not-a-uuid").with(owner.jwt()))
                .andExpect(status().isBadRequest());
        }

        @Test
        void requiresAuthentication() throws Exception {
            mvc.perform(get("/api/v1/pets/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class Update {

        @Test
        void replacesAllEditableFields() throws Exception {
            UUID home = household(owner);
            String id = createPet(owner, home, "Luna", "DOG");
            String body = """
                {"householdId":"%s","name":"Luna Maria","species":"DOG","breed":"Mix","color":"brown"}"""
                .formatted(home);

            mvc.perform(put("/api/v1/pets/{id}", id).with(owner.jwt())
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Luna Maria"))
                .andExpect(jsonPath("$.breed").value("Mix"))
                .andExpect(jsonPath("$.color").value("brown"))
                .andExpect(jsonPath("$.createdBy").value(owner.id().toString()))
                .andExpect(jsonPath("$.updatedAt", notNullValue()));

            // PUT is a full replace: fields left out are cleared.
            mvc.perform(put("/api/v1/pets/{id}", id).with(owner.jwt())
                    .contentType(MediaType.APPLICATION_JSON).content(petJson(home, "Luna", "DOG")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.breed", nullValue()));

            mvc.perform(get("/api/v1/pets/{id}", id).with(owner.jwt()))
                .andExpect(jsonPath("$.name").value("Luna"))
                .andExpect(jsonPath("$.color", nullValue()));
        }

        @Test
        void memberMayUpdateButSitterMayNot() throws Exception {
            UUID home = household(owner);
            String id = createPet(owner, home, "Luna", "DOG");
            TestUser member = TestUser.random("Member");
            TestUser sitter = TestUser.random("Sitter");
            identity.grant(member, home, HouseholdRole.MEMBER);
            identity.grant(sitter, home, HouseholdRole.SITTER);

            mvc.perform(put("/api/v1/pets/{id}", id).with(member.jwt())
                    .contentType(MediaType.APPLICATION_JSON).content(petJson(home, "Luna II", "DOG")))
                .andExpect(status().isOk());
            mvc.perform(put("/api/v1/pets/{id}", id).with(sitter.jwt())
                    .contentType(MediaType.APPLICATION_JSON).content(petJson(home, "Sitter was here", "DOG")))
                .andExpect(status().isForbidden());
        }

        @Test
        void nonMemberGets403() throws Exception {
            UUID home = household(owner);
            String id = createPet(owner, home, "Luna", "DOG");
            mvc.perform(put("/api/v1/pets/{id}", id).with(stranger.jwt())
                    .contentType(MediaType.APPLICATION_JSON).content(petJson(home, "Mine now", "DOG")))
                .andExpect(status().isForbidden());
        }

        @Test
        void movingToAnotherHouseholdNeedsWriteAccessThere() throws Exception {
            UUID home = household(owner);
            UUID second = household(owner);
            UUID sitting = UUID.randomUUID();
            identity.grant(owner, sitting, HouseholdRole.SITTER);
            String id = createPet(owner, home, "Luna", "DOG");

            mvc.perform(put("/api/v1/pets/{id}", id).with(owner.jwt())
                    .contentType(MediaType.APPLICATION_JSON).content(petJson(sitting, "Luna", "DOG")))
                .andExpect(status().isForbidden());
            mvc.perform(put("/api/v1/pets/{id}", id).with(owner.jwt())
                    .contentType(MediaType.APPLICATION_JSON).content(petJson(UUID.randomUUID(), "Luna", "DOG")))
                .andExpect(status().isForbidden());
            mvc.perform(put("/api/v1/pets/{id}", id).with(owner.jwt())
                    .contentType(MediaType.APPLICATION_JSON).content(petJson(second, "Luna", "DOG")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.householdId").value(second.toString()));
        }

        @Test
        void publishesPetUpdatedWithTheNewValues() throws Exception {
            UUID home = household(owner);
            UUID second = household(owner);
            String id = createPet(owner, home, "Luna", "DOG");

            mvc.perform(put("/api/v1/pets/{id}", id).with(owner.jwt())
                    .contentType(MediaType.APPLICATION_JSON).content(petJson(second, "Luna Maria", "CAT")))
                .andExpect(status().isOk());

            DocumentContext event = awaitEvent("pet.updated", data("petId", id));
            assertThat(event.read("$.producer", String.class)).isEqualTo("pawtrack-pet");
            assertThat(event.read("$.data.householdId", String.class)).isEqualTo(second.toString());
            assertThat(event.read("$.data.name", String.class)).isEqualTo("Luna Maria");
            assertThat(event.read("$.data.species", String.class)).isEqualTo("CAT");
        }

        @Test
        void unknownPetGets404() throws Exception {
            UUID home = household(owner);
            mvc.perform(put("/api/v1/pets/{id}", UUID.randomUUID()).with(owner.jwt())
                    .contentType(MediaType.APPLICATION_JSON).content(petJson(home, "Ghost", "DOG")))
                .andExpect(status().isNotFound());
        }

        @Test
        void invalidBodyGets400() throws Exception {
            UUID home = household(owner);
            String id = createPet(owner, home, "Luna", "DOG");
            mvc.perform(put("/api/v1/pets/{id}", id).with(owner.jwt())
                    .contentType(MediaType.APPLICATION_JSON).content(petJson(home, "", "DOG")))
                .andExpect(status().isBadRequest());
        }

        @Test
        void requiresAuthentication() throws Exception {
            mvc.perform(put("/api/v1/pets/{id}", UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON).content(petJson(UUID.randomUUID(), "Rex", "DOG")))
                .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class Delete {

        @Test
        void softDeletesAndPublishesPetDeleted() throws Exception {
            UUID home = household(owner);
            String id = createPet(owner, home, "Luna", "DOG");

            mvc.perform(delete("/api/v1/pets/{id}", id).with(owner.jwt()))
                .andExpect(status().isNoContent());

            DocumentContext event = awaitEvent("pet.deleted", data("petId", id));
            assertThat(event.read("$.data.householdId", String.class)).isEqualTo(home.toString());
            assertThat(event.read("$.producer", String.class)).isEqualTo("pawtrack-pet");

            mvc.perform(get("/api/v1/pets/{id}", id).with(owner.jwt())).andExpect(status().isNotFound());
            mvc.perform(delete("/api/v1/pets/{id}", id).with(owner.jwt())).andExpect(status().isNotFound());
        }

        @Test
        void sitterAndNonMemberGet403() throws Exception {
            UUID home = household(owner);
            String id = createPet(owner, home, "Luna", "DOG");
            TestUser sitter = TestUser.random("Sitter");
            identity.grant(sitter, home, HouseholdRole.SITTER);

            mvc.perform(delete("/api/v1/pets/{id}", id).with(sitter.jwt())).andExpect(status().isForbidden());
            mvc.perform(delete("/api/v1/pets/{id}", id).with(stranger.jwt())).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/pets/{id}", id).with(owner.jwt())).andExpect(status().isOk());
        }

        @Test
        void unknownPetGets404() throws Exception {
            mvc.perform(delete("/api/v1/pets/{id}", UUID.randomUUID()).with(owner.jwt()))
                .andExpect(status().isNotFound());
        }

        @Test
        void requiresAuthentication() throws Exception {
            mvc.perform(delete("/api/v1/pets/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
        }
    }
}
