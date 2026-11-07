package com.pawtrack.health.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.pawtrack.health.entity.VetVisit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Queries only see records that are not soft-deleted. */
public interface VetVisitRepository extends JpaRepository<VetVisit, UUID> {

    List<VetVisit> findByPetIdAndDeletedAtIsNullOrderByVisitDateDescCreatedAtDesc(UUID petId);

    Optional<VetVisit> findByIdAndPetIdAndDeletedAtIsNull(UUID id, UUID petId);

    /** Latest visit that is not in the future. */
    Optional<VetVisit> findFirstByPetIdAndDeletedAtIsNullAndVisitDateLessThanEqualOrderByVisitDateDesc(UUID petId,
                                                                                                      Instant now);

    @Modifying
    @Query("update VetVisit v set v.deletedAt = :now where v.petId = :petId and v.deletedAt is null")
    int softDeleteByPetId(UUID petId, Instant now);
}
