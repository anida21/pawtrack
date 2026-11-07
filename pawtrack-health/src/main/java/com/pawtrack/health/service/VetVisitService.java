package com.pawtrack.health.service;

import java.util.List;
import java.util.UUID;

import com.pawtrack.health.dto.VetVisitRequest;
import com.pawtrack.health.dto.VetVisitResponse;
import com.pawtrack.health.entity.VetVisit;
import com.pawtrack.health.exception.NotFoundException;
import com.pawtrack.health.mapper.HealthMapper;
import com.pawtrack.health.repository.VetVisitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Vet visits of a pet. Every method checks access via {@link PetAccess} first. No events. */
@Service
@RequiredArgsConstructor
public class VetVisitService {

    private final VetVisitRepository repository;
    private final HealthMapper mapper;
    private final PetAccess access;

    @Transactional(readOnly = true)
    public List<VetVisitResponse> list(Jwt jwt, UUID petId) {
        access.check(jwt, petId);
        return repository.findByPetIdAndDeletedAtIsNullOrderByVisitDateDescCreatedAtDesc(petId).stream()
            .map(mapper::toResponse)
            .toList();
    }

    @Transactional
    public VetVisitResponse create(Jwt jwt, UUID petId, VetVisitRequest request) {
        access.checkForWrite(jwt, petId);
        VetVisit visit = new VetVisit(petId, UUID.fromString(jwt.getSubject()));
        mapper.apply(request, visit);
        repository.save(visit);
        return mapper.toResponse(visit);
    }

    @Transactional
    public VetVisitResponse update(Jwt jwt, UUID petId, UUID id, VetVisitRequest request) {
        access.checkForWrite(jwt, petId);
        VetVisit visit = find(petId, id);
        mapper.apply(request, visit);
        visit.touch();
        return mapper.toResponse(visit);
    }

    @Transactional
    public void delete(Jwt jwt, UUID petId, UUID id) {
        access.checkForWrite(jwt, petId);
        find(petId, id).markDeleted();
    }

    private VetVisit find(UUID petId, UUID id) {
        return repository.findByIdAndPetIdAndDeletedAtIsNull(id, petId)
            .orElseThrow(() -> new NotFoundException("Vet visit not found"));
    }
}
