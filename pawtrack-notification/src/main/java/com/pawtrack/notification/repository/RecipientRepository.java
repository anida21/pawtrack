package com.pawtrack.notification.repository;

import java.util.List;
import java.util.UUID;

import com.pawtrack.notification.entity.Recipient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipientRepository extends JpaRepository<Recipient, UUID> {

    /** Members of the household who want emails, ordered for stable output. */
    @Query("""
        select r from Recipient r
        where r.emailNotifications = true
          and r.userId in (select m.id.userId from HouseholdMember m where m.id.householdId = :householdId)
        order by r.email
        """)
    List<Recipient> findEmailRecipients(@Param("householdId") UUID householdId);
}
