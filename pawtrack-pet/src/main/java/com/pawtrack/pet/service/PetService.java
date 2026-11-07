package com.pawtrack.pet.service;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.pawtrack.pet.dto.HouseholdRole;
import com.pawtrack.pet.dto.PetPage;
import com.pawtrack.pet.dto.PetRequest;
import com.pawtrack.pet.dto.PetResponse;
import com.pawtrack.pet.entity.Pet;
import com.pawtrack.pet.entity.Species;
import com.pawtrack.pet.exception.NotFoundException;
import com.pawtrack.pet.mapper.PetMapper;
import com.pawtrack.pet.messaging.EventPublisher;
import com.pawtrack.pet.messaging.PetEvent;
import com.pawtrack.pet.repository.PetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pet CRUD. Every method checks the caller's household role via {@link HouseholdAccess} and returns DTOs,
 * so mapping runs inside the transaction. A pet that does not exist or is soft-deleted is a 404;
 * a pet in a household the caller does not belong to is a 403.
 */
@Service
@RequiredArgsConstructor
public class PetService {

    private static final Sort LIST_ORDER = Sort.by("name").ascending().and(Sort.by("createdAt").ascending());

    private final PetRepository petRepository;
    private final PetMapper petMapper;
    private final HouseholdAccess access;
    private final EventPublisher eventPublisher;

    /**
     * Pets of all households of the caller. A {@code householdId} filter the caller is not a member of
     * simply matches nothing.
     */
    @Transactional(readOnly = true)
    public PetPage list(Jwt jwt, UUID householdId, Species species, int page, int size) {
        Map<UUID, HouseholdRole> roles = access.roles(jwt);
        Set<UUID> households = householdId == null ? roles.keySet()
            : roles.containsKey(householdId) ? Set.of(householdId) : Set.of();
        PageRequest pageable = PageRequest.of(page, size, LIST_ORDER);
        if (households.isEmpty()) {
            return PetPage.of(Page.<PetResponse>empty(pageable));
        }
        Page<Pet> pets = species == null
            ? petRepository.findByHouseholdIdInAndDeletedAtIsNull(households, pageable)
            : petRepository.findByHouseholdIdInAndSpeciesAndDeletedAtIsNull(households, species, pageable);
        return PetPage.of(pets.map(petMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public PetResponse get(Jwt jwt, UUID petId) {
        Pet pet = activePet(petId);
        access.requireRead(jwt, pet.getHouseholdId());
        return petMapper.toResponse(pet);
    }

    @Transactional
    public PetResponse create(Jwt jwt, PetRequest request) {
        access.requireWrite(jwt, request.householdId());
        Pet pet = new Pet(UUID.fromString(jwt.getSubject()));
        petMapper.apply(request, pet);
        petRepository.save(pet);
        eventPublisher.publish(PetEvent.PetCreated.of(pet));
        return petMapper.toResponse(pet);
    }

    /** Full update. Moving a pet to another household needs write access in both households. */
    @Transactional
    public PetResponse update(Jwt jwt, UUID petId, PetRequest request) {
        Pet pet = activePet(petId);
        access.requireWrite(jwt, pet.getHouseholdId());
        if (!pet.getHouseholdId().equals(request.householdId())) {
            access.requireWrite(jwt, request.householdId());
        }
        petMapper.apply(request, pet);
        pet.touch();
        eventPublisher.publish(PetEvent.PetUpdated.of(pet));
        return petMapper.toResponse(pet);
    }

    @Transactional
    public void delete(Jwt jwt, UUID petId) {
        Pet pet = activePet(petId);
        access.requireWrite(jwt, pet.getHouseholdId());
        pet.markDeleted();
        eventPublisher.publish(PetEvent.PetDeleted.of(pet));
    }

    /** Loads an active pet; package-visible for {@link PhotoService}. */
    Pet activePet(UUID petId) {
        return petRepository.findByIdAndDeletedAtIsNull(petId)
            .orElseThrow(() -> new NotFoundException("Pet not found"));
    }
}
