package com.pawtrack.health.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.pawtrack.health.entity.Weight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Queries only see records that are not soft-deleted. */
public interface WeightRepository extends JpaRepository<Weight, UUID> {

    /** {@code from} and {@code to} are inclusive. */
    List<Weight> findByPetIdAndDeletedAtIsNullAndMeasuredOnBetweenOrderByMeasuredOnAscCreatedAtAsc(UUID petId,
                                                                                                  LocalDate from,
                                                                                                  LocalDate to);

    Optional<Weight> findFirstByPetIdAndDeletedAtIsNullOrderByMeasuredOnDescCreatedAtDesc(UUID petId);

    @Modifying
    @Query("update Weight w set w.deletedAt = :now where w.petId = :petId and w.deletedAt is null")
    int softDeleteByPetId(UUID petId, Instant now);
}
