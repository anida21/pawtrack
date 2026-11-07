package com.pawtrack.health.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.pawtrack.health.support.IntegrationTest;
import com.pawtrack.health.support.TestUser;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SummaryControllerTest extends IntegrationTest {

    private final TestUser owner = TestUser.random("Owner");
    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);

    @Test
    void emptySummaryForAPetWithoutRecords() throws Exception {
        UUID petId = pets.create(owner, "Luna");
        mvc.perform(get(path(petId, "summary")).with(owner.jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.petId").value(petId.toString()))
            .andExpect(jsonPath("$.latestWeightKg").doesNotExist())
            .andExpect(jsonPath("$.lastVetVisit").doesNotExist())
            .andExpect(jsonPath("$.nextVaccinationDue").doesNotExist())
            .andExpect(jsonPath("$.overdueVaccinations").value(0))
            .andExpect(jsonPath("$.activeMedications").value(0));
    }

    @Test
    void summarisesThePetsRecords() throws Exception {
        UUID petId = pets.create(owner, "Luna");
        UUID otherPet = pets.create(owner, "Momo");

        // weights: the latest measurement counts, not the latest entry
        weight(petId, today.minusDays(1), "11.2");
        weight(petId, today.minusDays(30), "10.5");
        weight(otherPet, today, "4");

        // vet visits: the latest one that is not in the future
        Instant lastVisit = Instant.now().minus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        visit(petId, lastVisit.minus(60, ChronoUnit.DAYS));
        visit(petId, lastVisit);
        visit(petId, Instant.now().plus(10, ChronoUnit.DAYS));

        // vaccinations: per vaccine only the latest administration counts
        vaccination(petId, "Rabies", today.minusYears(1), today.minusDays(5));     // superseded by the next one
        vaccination(petId, "rabies ", today.minusDays(5), today.plusYears(1));
        vaccination(petId, "Parvo", today.minusYears(1), today.plusDays(20));      // next due
        vaccination(petId, "Distemper", today.minusYears(2), today.minusDays(1));  // overdue
        vaccination(petId, "Lepto", today.minusYears(1), today.minusDays(40));     // overdue
        vaccination(petId, "Kennel cough", today.minusDays(10), null);
        vaccination(otherPet, "Rabies", today.minusYears(2), today.minusDays(100));

        // medications: active = not finished
        medication(petId, today.minusDays(10), today.plusDays(1), "DAILY");
        medication(petId, today.minusDays(10), null, "MONTHLY");
        medication(petId, today.minusDays(10), today.minusDays(1), "DAILY");
        medication(petId, today.minusDays(2), null, "ONCE");

        mvc.perform(get(path(petId, "summary")).with(owner.jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.petId").value(petId.toString()))
            .andExpect(jsonPath("$.latestWeightKg").value(11.2))
            .andExpect(jsonPath("$.lastVetVisit").value(lastVisit.toString()))
            .andExpect(jsonPath("$.nextVaccinationDue").value(today.plusDays(20).toString()))
            .andExpect(jsonPath("$.overdueVaccinations").value(2))
            .andExpect(jsonPath("$.activeMedications").value(2));
    }

    private void weight(UUID petId, LocalDate day, String kg) throws Exception {
        create(owner, path(petId, "weights"), "{\"measuredOn\":\"%s\",\"weightKg\":%s}".formatted(day, kg));
    }

    private void visit(UUID petId, Instant when) throws Exception {
        create(owner, path(petId, "vet-visits"), "{\"visitDate\":\"%s\",\"reason\":\"Checkup\"}".formatted(when));
    }

    private void vaccination(UUID petId, String vaccine, LocalDate given, LocalDate due) throws Exception {
        String dueJson = due == null ? "" : ",\"nextDueDate\":\"%s\"".formatted(due);
        create(owner, path(petId, "vaccinations"),
            "{\"vaccine\":\"%s\",\"administeredOn\":\"%s\"%s}".formatted(vaccine, given, dueJson));
    }

    private void medication(UUID petId, LocalDate start, LocalDate end, String frequency) throws Exception {
        String endJson = end == null ? "" : ",\"endDate\":\"%s\"".formatted(end);
        create(owner, path(petId, "medications"),
            "{\"name\":\"M\",\"dosage\":\"1\",\"startDate\":\"%s\",\"frequency\":\"%s\"%s}"
                .formatted(start, frequency, endJson));
    }
}
