package com.pawtrack.health.service;

import java.util.List;
import java.util.UUID;

import com.pawtrack.health.dto.PetRef;
import com.pawtrack.health.dto.VaccinationRequest;
import com.pawtrack.health.dto.VaccinationResponse;
import com.pawtrack.health.entity.Vaccination;
import com.pawtrack.health.exception.NotFoundException;
import com.pawtrack.health.mapper.HealthMapper;
import com.pawtrack.health.messaging.EventPublisher;
import com.pawtrack.health.messaging.HealthEvent;
import com.pawtrack.health.repository.VaccinationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Vaccinations of a pet. Every method checks access via {@link PetAccess} first. */
@Service
@RequiredArgsConstructor
public class VaccinationService {

    private final VaccinationRepository repository;
    private final HealthMapper mapper;
    private final PetAccess access;
    private final EventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<VaccinationResponse> list(Jwt jwt, UUID petId) {
        access.check(jwt, petId);
        return repository.findByPetIdAndDeletedAtIsNullOrderByAdministeredOnDescCreatedAtDesc(petId).stream()
            .map(mapper::toResponse)
            .toList();
    }

    /** Publishes health.vaccination.recorded. */
    @Transactional
    public VaccinationResponse create(Jwt jwt, UUID petId, VaccinationRequest request) {
        PetRef pet = access.checkForWrite(jwt, petId);
        Vaccination vaccination = new Vaccination(petId, UUID.fromString(jwt.getSubject()));
        mapper.apply(request, vaccination);
        repository.save(vaccination);
        eventPublisher.publish(HealthEvent.VaccinationRecorded.of(vaccination, pet.householdId()));
        return mapper.toResponse(vaccination);
    }

    @Transactional
    public VaccinationResponse update(Jwt jwt, UUID petId, UUID id, VaccinationRequest request) {
        access.checkForWrite(jwt, petId);
        Vaccination vaccination = find(petId, id);
        mapper.apply(request, vaccination);
        vaccination.touch();
        return mapper.toResponse(vaccination);
    }

    @Transactional
    public void delete(Jwt jwt, UUID petId, UUID id) {
        access.checkForWrite(jwt, petId);
        find(petId, id).markDeleted();
    }

    private Vaccination find(UUID petId, UUID id) {
        return repository.findByIdAndPetIdAndDeletedAtIsNull(id, petId)
            .orElseThrow(() -> new NotFoundException("Vaccination not found"));
    }
}
