package com.pawtrack.health.repository;

import java.time.Instant;
import java.util.UUID;

import com.pawtrack.health.entity.PetCopy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * Native upserts, so the event consumer and request threads can write the same pet concurrently
 * without duplicate-key failures. A deleted pet stays deleted: later upserts do not revive it.
 */
public interface PetCopyRepository extends JpaRepository<PetCopy, UUID> {

    @Modifying
    @Query(nativeQuery = true, value = """
        insert into pets (pet_id, household_id, name, updated_at)
        values (:petId, :householdId, :name, :now)
        on conflict (pet_id) do update
           set household_id = excluded.household_id, name = excluded.name, updated_at = excluded.updated_at
         where pets.deleted_at is null""")
    int upsert(UUID petId, UUID householdId, String name, Instant now);

    /** Also creates a tombstone when pet.deleted arrives before (or without) pet.created. */
    @Modifying
    @Query(nativeQuery = true, value = """
        insert into pets (pet_id, household_id, updated_at, deleted_at)
        values (:petId, :householdId, :now, :now)
        on conflict (pet_id) do update
           set deleted_at = coalesce(pets.deleted_at, excluded.deleted_at), updated_at = excluded.updated_at""")
    int markDeleted(UUID petId, UUID householdId, Instant now);
}
