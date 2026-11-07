package com.pawtrack.health.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.pawtrack.health.entity.Medication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Queries only see records that are not soft-deleted. */
public interface MedicationRepository extends JpaRepository<Medication, UUID> {

    List<Medication> findByPetIdAndDeletedAtIsNullOrderByStartDateDescCreatedAtDesc(UUID petId);

    Optional<Medication> findByIdAndPetIdAndDeletedAtIsNull(UUID id, UUID petId);

    @Modifying
    @Query("update Medication m set m.deletedAt = :now where m.petId = :petId and m.deletedAt is null")
    int softDeleteByPetId(UUID petId, Instant now);
}
