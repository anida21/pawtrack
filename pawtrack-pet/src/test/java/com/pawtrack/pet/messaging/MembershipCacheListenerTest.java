package com.pawtrack.pet.messaging;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.pawtrack.pet.config.IdentityClient;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import tools.jackson.databind.json.JsonMapper;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** household.member.removed evicts the removed user's cached memberships. */
class MembershipCacheListenerTest {

    private final IdentityClient identityClient = mock(IdentityClient.class);
    private final MembershipCacheListener listener =
        new MembershipCacheListener(JsonMapper.builder().build(), identityClient);

    @Test
    void evictsTheRemovedUser() {
        UUID userId = UUID.randomUUID();
        listener.onMessage(message("""
            {"eventId":"%s","type":"household.member.removed","occurredAt":"2026-11-07T10:00:00Z",
             "producer":"pawtrack-identity","data":{"householdId":"%s","userId":"%s"}}"""
            .formatted(UUID.randomUUID(), UUID.randomUUID(), userId)));

        verify(identityClient).evict(userId.toString());
    }

    @Test
    void ignoresUnreadableOrIncompleteMessages() {
        listener.onMessage(message("not json"));
        listener.onMessage(message("""
            {"eventId":"%s","type":"household.member.removed","data":{}}""".formatted(UUID.randomUUID())));

        verify(identityClient, never()).evict(anyString());
    }

    private static Message message(String body) {
        return MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8))
            .setHeader("__TypeId__", "com.pawtrack.identity.messaging.EventEnvelope")
            .build();
    }
}
