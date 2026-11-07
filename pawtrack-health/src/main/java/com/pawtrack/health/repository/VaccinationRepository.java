package com.pawtrack.health.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.pawtrack.health.entity.Vaccination;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Queries only see records that are not soft-deleted. */
public interface VaccinationRepository extends JpaRepository<Vaccination, UUID> {

    List<Vaccination> findByPetIdAndDeletedAtIsNullOrderByAdministeredOnDescCreatedAtDesc(UUID petId);

    Optional<Vaccination> findByIdAndPetIdAndDeletedAtIsNull(UUID id, UUID petId);

    @Modifying
    @Query("update Vaccination v set v.deletedAt = :now where v.petId = :petId and v.deletedAt is null")
    int softDeleteByPetId(UUID petId, Instant now);
}
