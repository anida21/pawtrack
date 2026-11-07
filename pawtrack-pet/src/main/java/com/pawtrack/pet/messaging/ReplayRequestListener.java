package com.pawtrack.pet.messaging;

import java.util.List;
import java.util.UUID;

import com.pawtrack.pet.service.ReplayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Answers {@code replay.requested} (backfill, events.md) with a {@code pet.updated} per pet when that type is
 * requested. The raw body is parsed here (no dependency on the requester's type headers).
 * Not idempotent on purpose: answering twice only republishes upserts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReplayRequestListener {

    static final String PET_UPDATED = "pet.updated";

    private final JsonMapper jsonMapper;
    private final ReplayService replayService;

    @RabbitListener(queues = "${pawtrack.events.replay-queue}")
    public void onMessage(Message message) {
        ReplayRequest request;
        try {
            request = jsonMapper.readValue(message.getBody(), ReplayRequest.class);
        } catch (JacksonException ex) {
            throw new AmqpRejectAndDontRequeueException("Unreadable replay request", ex);
        }
        if (request == null || request.data() == null || request.data().types() == null) {
            throw new AmqpRejectAndDontRequeueException("Not a valid replay.requested event: " + request);
        }
        if (!request.data().types().contains(PET_UPDATED)) {
            log.debug("Replay request from {} needs nothing from pet", request.data().requestedBy());
            return;
        }
        log.info("Replay of pets requested by {}", request.data().requestedBy());
        replayService.replayPets();
    }

    /** Incoming envelope; unknown fields are ignored. */
    record ReplayRequest(UUID eventId, String type, Data data) {
        record Data(String requestedBy, List<String> types) {
        }
    }
}
