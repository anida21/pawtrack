package com.pawtrack.health.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.pawtrack.health.dto.MedicationRequest;
import com.pawtrack.health.dto.MedicationResponse;
import com.pawtrack.health.dto.PetRef;
import com.pawtrack.health.entity.Medication;
import com.pawtrack.health.exception.NotFoundException;
import com.pawtrack.health.mapper.HealthMapper;
import com.pawtrack.health.messaging.EventPublisher;
import com.pawtrack.health.messaging.HealthEvent;
import com.pawtrack.health.repository.MedicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Medications of a pet. Create and update publish health.medication.scheduled (notification replaces the
 * reminders), delete publishes health.medication.cancelled.
 */
@Service
@RequiredArgsConstructor
public class MedicationService {

    private final MedicationRepository repository;
    private final HealthMapper mapper;
    private final PetAccess access;
    private final EventPublisher eventPublisher;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<MedicationResponse> list(Jwt jwt, UUID petId, boolean activeOnly) {
        access.check(jwt, petId);
        LocalDate today = LocalDate.now(clock);
        return repository.findByPetIdAndDeletedAtIsNullOrderByStartDateDescCreatedAtDesc(petId).stream()
            .filter(medication -> !activeOnly || medication.isActiveOn(today))
            .map(medication -> mapper.toResponse(medication, today))
            .toList();
    }

    @Transactional
    public MedicationResponse create(Jwt jwt, UUID petId, MedicationRequest request) {
        PetRef pet = access.checkForWrite(jwt, petId);
        Medication medication = new Medication(petId, UUID.fromString(jwt.getSubject()));
        mapper.apply(request, medication);
        repository.save(medication);
        eventPublisher.publish(HealthEvent.MedicationScheduled.of(medication, pet.householdId()));
        return mapper.toResponse(medication, LocalDate.now(clock));
    }

    @Transactional
    public MedicationResponse update(Jwt jwt, UUID petId, UUID id, MedicationRequest request) {
        PetRef pet = access.checkForWrite(jwt, petId);
        Medication medication = find(petId, id);
        mapper.apply(request, medication);
        medication.touch();
        eventPublisher.publish(HealthEvent.MedicationScheduled.of(medication, pet.householdId()));
        return mapper.toResponse(medication, LocalDate.now(clock));
    }

    @Transactional
    public void delete(Jwt jwt, UUID petId, UUID id) {
        access.checkForWrite(jwt, petId);
        Medication medication = find(petId, id);
        medication.markDeleted();
        eventPublisher.publish(HealthEvent.MedicationCancelled.of(medication));
    }

    private Medication find(UUID petId, UUID id) {
        return repository.findByIdAndPetIdAndDeletedAtIsNull(id, petId)
            .orElseThrow(() -> new NotFoundException("Medication not found"));
    }
}
