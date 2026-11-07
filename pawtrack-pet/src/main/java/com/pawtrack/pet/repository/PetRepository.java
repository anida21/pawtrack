package com.pawtrack.pet.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.pawtrack.pet.entity.Pet;
import com.pawtrack.pet.entity.Species;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Queries only see active pets unless the method name says otherwise. */
public interface PetRepository extends JpaRepository<Pet, UUID> {

    Optional<Pet> findByIdAndDeletedAtIsNull(UUID id);

    Page<Pet> findByHouseholdIdInAndDeletedAtIsNull(Collection<UUID> householdIds, Pageable pageable);

    Page<Pet> findByHouseholdIdInAndSpeciesAndDeletedAtIsNull(Collection<UUID> householdIds, Species species,
                                                               Pageable pageable);

    List<Pet> findByHouseholdIdAndDeletedAtIsNull(UUID householdId);

    /** All pets that are not deleted (backfill snapshot). */
    List<Pet> findByDeletedAtIsNull();
}
