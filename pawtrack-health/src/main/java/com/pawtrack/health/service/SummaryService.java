package com.pawtrack.health.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.pawtrack.health.dto.HealthSummary;
import com.pawtrack.health.entity.Medication;
import com.pawtrack.health.entity.Vaccination;
import com.pawtrack.health.entity.VetVisit;
import com.pawtrack.health.entity.Weight;
import com.pawtrack.health.repository.MedicationRepository;
import com.pawtrack.health.repository.VaccinationRepository;
import com.pawtrack.health.repository.VetVisitRepository;
import com.pawtrack.health.repository.WeightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Dashboard card of a pet; the rules are spelled out in health.yaml ({@code getHealthSummary}). */
@Service
@RequiredArgsConstructor
public class SummaryService {

    private final VaccinationRepository vaccinations;
    private final VetVisitRepository vetVisits;
    private final MedicationRepository medications;
    private final WeightRepository weights;
    private final PetAccess access;
    private final Clock clock;

    @Transactional(readOnly = true)
    public HealthSummary summary(Jwt jwt, UUID petId) {
        access.check(jwt, petId);
        LocalDate today = LocalDate.now(clock);

        Double latestWeight = weights.findFirstByPetIdAndDeletedAtIsNullOrderByMeasuredOnDescCreatedAtDesc(petId)
            .map(Weight::getWeightKg).map(Number::doubleValue).orElse(null);
        var lastVisit = vetVisits
            .findFirstByPetIdAndDeletedAtIsNullAndVisitDateLessThanEqualOrderByVisitDateDesc(petId, clock.instant())
            .map(VetVisit::getVisitDate).orElse(null);

        Collection<Vaccination> current = latestPerVaccine(petId);
        LocalDate nextDue = current.stream()
            .map(Vaccination::getNextDueDate).filter(Objects::nonNull)
            .filter(due -> !due.isBefore(today))
            .min(LocalDate::compareTo).orElse(null);
        int overdue = (int) current.stream()
            .map(Vaccination::getNextDueDate).filter(Objects::nonNull)
            .filter(due -> due.isBefore(today))
            .count();

        int activeMedications = (int) medications.findByPetIdAndDeletedAtIsNullOrderByStartDateDescCreatedAtDesc(petId)
            .stream().filter(medication -> medication.isActiveOn(today)).count();

        return new HealthSummary(petId, latestWeight, lastVisit, nextDue, overdue, activeMedications);
    }

    /** The most recent administration of each vaccine (name compared case-insensitively); older ones are superseded. */
    private Collection<Vaccination> latestPerVaccine(UUID petId) {
        Map<String, Vaccination> latest = new LinkedHashMap<>();
        for (Vaccination vaccination
            : vaccinations.findByPetIdAndDeletedAtIsNullOrderByAdministeredOnDescCreatedAtDesc(petId)) {
            latest.putIfAbsent(vaccination.getVaccine().trim().toLowerCase(Locale.ROOT), vaccination);
        }
        return latest.values();
    }
}
