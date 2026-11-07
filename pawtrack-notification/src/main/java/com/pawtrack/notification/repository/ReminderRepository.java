package com.pawtrack.notification.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.pawtrack.notification.entity.Reminder;
import com.pawtrack.notification.entity.ReminderSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReminderRepository extends JpaRepository<Reminder, UUID>, JpaSpecificationExecutor<Reminder> {

    /**
     * Due PENDING reminders, row-locked. {@code SKIP LOCKED} lets several instances dispatch in parallel
     * without sending the same reminder twice.
     */
    @Query(value = """
        select * from reminders
        where status = 'PENDING' and due_at <= :now
        order by due_at, id
        limit :limit
        for update skip locked
        """, nativeQuery = true)
    List<Reminder> lockDue(@Param("now") Instant now, @Param("limit") int limit);

    /** Cancels all PENDING reminders of one vaccination / medication. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update Reminder r set r.status = com.pawtrack.notification.entity.ReminderStatus.CANCELLED, r.updatedAt = :now
        where r.source = :source and r.sourceId = :sourceId
          and r.status = com.pawtrack.notification.entity.ReminderStatus.PENDING
        """)
    int cancelPendingBySource(@Param("source") ReminderSource source, @Param("sourceId") UUID sourceId,
                              @Param("now") Instant now);

    /** Cancels the PENDING reminders of one vaccination / medication that are due after {@code after}. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update Reminder r set r.status = com.pawtrack.notification.entity.ReminderStatus.CANCELLED, r.updatedAt = :now
        where r.source = :source and r.sourceId = :sourceId and r.dueAt > :after
          and r.status = com.pawtrack.notification.entity.ReminderStatus.PENDING
        """)
    int cancelPendingBySourceDueAfter(@Param("source") ReminderSource source, @Param("sourceId") UUID sourceId,
                                      @Param("after") Instant after, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update Reminder r set r.status = com.pawtrack.notification.entity.ReminderStatus.CANCELLED, r.updatedAt = :now
        where r.petId = :petId and r.status = com.pawtrack.notification.entity.ReminderStatus.PENDING
        """)
    int cancelPendingByPet(@Param("petId") UUID petId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update Reminder r set r.status = com.pawtrack.notification.entity.ReminderStatus.CANCELLED, r.updatedAt = :now
        where r.householdId = :householdId and r.status = com.pawtrack.notification.entity.ReminderStatus.PENDING
        """)
    int cancelPendingByHousehold(@Param("householdId") UUID householdId, @Param("now") Instant now);

    List<Reminder> findBySourceAndSourceIdOrderByDueAt(ReminderSource source, UUID sourceId);
}
