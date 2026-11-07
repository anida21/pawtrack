package com.pawtrack.pet.messaging;

import java.util.UUID;

import com.pawtrack.pet.config.IdentityClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * On {@code household.member.removed} forgets the removed user's cached memberships, so they lose access to
 * the household's pets at once instead of after the cache TTL. Listens on this instance's own queue
 * ({@code RabbitConfig.membershipChangesQueue}). Eviction is harmless to repeat, so no idempotency is needed,
 * and an unreadable message is only logged (there is no DLQ for a per-instance queue).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MembershipCacheListener {

    private final JsonMapper jsonMapper;
    private final IdentityClient identityClient;

    @RabbitListener(queues = "#{membershipChangesQueue.name}")
    public void onMessage(Message message) {
        MemberRemoved event;
        try {
            event = jsonMapper.readValue(message.getBody(), MemberRemoved.class);
        } catch (JacksonException ex) {
            log.warn("Unreadable household.member.removed event ignored", ex);
            return;
        }
        if (event == null || event.data() == null || event.data().userId() == null) {
            log.warn("household.member.removed without userId ignored: {}", event);
            return;
        }
        identityClient.evict(event.data().userId().toString());
        log.debug("Evicted memberships of {} (removed from {})", event.data().userId(), event.data().householdId());
    }

    /** Incoming envelope; unknown fields are ignored. */
    record MemberRemoved(UUID eventId, String type, Data data) {
        record Data(UUID householdId, UUID userId) {
        }
    }
}
