package com.pawtrack.notification.repository;

import java.time.Instant;
import java.util.UUID;

import com.pawtrack.notification.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Consumed eventIds. {@code ON CONFLICT DO NOTHING} makes the "first time?" check and the claim one atomic
 * statement: a concurrent duplicate waits for the first transaction, then gets 0.
 */
public interface ProcessedEventRepository extends Repository<ProcessedEvent, UUID> {

    /** @return 1 if the event was not processed before (now claimed), 0 for a duplicate. */
    @Modifying
    @Query(value = """
        insert into processed_events (event_id, type, processed_at) values (:eventId, :type, :now)
        on conflict (event_id) do nothing
        """, nativeQuery = true)
    int claim(@Param("eventId") UUID eventId, @Param("type") String type, @Param("now") Instant now);

    boolean existsById(UUID eventId);
}
