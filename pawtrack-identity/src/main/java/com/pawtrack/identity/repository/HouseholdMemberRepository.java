package com.pawtrack.identity.repository;

import java.util.List;
import java.util.UUID;

import com.pawtrack.identity.entity.HouseholdMember;
import com.pawtrack.identity.entity.HouseholdMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Read-only access to memberships across households. Writes go through the {@code Household} aggregate.
 */
public interface HouseholdMemberRepository extends JpaRepository<HouseholdMember, HouseholdMemberId> {

    @Query("""
        select m from HouseholdMember m
        join fetch m.household
        where m.user.id = :userId
        order by m.joinedAt
        """)
    List<HouseholdMember> findAllByUserIdWithHousehold(UUID userId);
}
