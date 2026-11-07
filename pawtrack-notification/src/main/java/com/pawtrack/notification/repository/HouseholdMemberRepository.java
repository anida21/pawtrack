package com.pawtrack.notification.repository;

import java.util.List;
import java.util.UUID;

import com.pawtrack.notification.entity.HouseholdMember;
import com.pawtrack.notification.entity.HouseholdMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HouseholdMemberRepository extends JpaRepository<HouseholdMember, HouseholdMemberId> {

    @Query("select m.id.householdId from HouseholdMember m where m.id.userId = :userId")
    List<UUID> findHouseholdIdsByUserId(@Param("userId") UUID userId);

    @Query("select m from HouseholdMember m where m.id.householdId = :householdId order by m.joinedAt")
    List<HouseholdMember> findByHouseholdId(@Param("householdId") UUID householdId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from HouseholdMember m where m.id.householdId = :householdId")
    int deleteByHouseholdId(@Param("householdId") UUID householdId);
}
