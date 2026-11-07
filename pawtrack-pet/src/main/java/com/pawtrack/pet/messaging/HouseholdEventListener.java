package com.pawtrack.pet.messaging;

import com.pawtrack.pet.service.HouseholdCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumes {@code household.deleted} from {@code pet.household-events}. The raw body is parsed here
 * (instead of relying on the producer's type headers), so only the envelope fields from events.md matter.
 * Unreadable messages are dead-lettered at once; processing failures are retried, then dead-lettered.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HouseholdEventListener {

    private final JsonMapper jsonMapper;
    private final HouseholdCleanupService cleanupService;

    @RabbitListener(queues = "${pawtrack.events.household-queue}")
    public void onMessage(Message message) {
        HouseholdDeletedEvent event = parse(message);
        cleanupService.householdDeleted(event.eventId(), event.data().householdId());
    }

    private HouseholdDeletedEvent parse(Message message) {
        HouseholdDeletedEvent event;
        try {
            event = jsonMapper.readValue(message.getBody(), HouseholdDeletedEvent.class);
        } catch (JacksonException ex) {
            throw new AmqpRejectAndDontRequeueException("Unreadable household event", ex);
        }
        if (event == null || !event.isValid()) {
            throw new AmqpRejectAndDontRequeueException("Not a valid household.deleted event: " + event);
        }
        return event;
    }
}
