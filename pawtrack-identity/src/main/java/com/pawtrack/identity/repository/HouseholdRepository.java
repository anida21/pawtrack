package com.pawtrack.identity.repository;

import java.util.Optional;
import java.util.UUID;

import com.pawtrack.identity.entity.Household;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HouseholdRepository extends JpaRepository<Household, UUID> {

    @EntityGraph(attributePaths = {"members", "members.user"})
    Optional<Household> findWithMembersById(UUID id);
}
