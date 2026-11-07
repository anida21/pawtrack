package com.pawtrack.health.messaging;

import com.pawtrack.health.service.PetCopyService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumes {@code pet.created}, {@code pet.updated} (both upserts) and {@code pet.deleted} from {@code health.pet-events}. The raw body is parsed here
 * (instead of relying on the producer's type headers), so only the envelope fields from events.md matter.
 * Unreadable messages are dead-lettered at once; processing failures are retried, then dead-lettered.
 */
@Component
@RequiredArgsConstructor
public class PetEventListener {

    private final JsonMapper jsonMapper;
    private final PetCopyService petCopyService;

    @RabbitListener(queues = "${pawtrack.events.pet-queue}")
    public void onMessage(Message message) {
        PetEventMessage event = parse(message);
        PetEventMessage.Data data = event.data();
        if (event.isUpsert()) {
            petCopyService.petUpserted(event.eventId(), event.type(), data.petId(), data.householdId(), data.name());
        } else {
            petCopyService.petDeleted(event.eventId(), data.petId(), data.householdId());
        }
    }

    private PetEventMessage parse(Message message) {
        PetEventMessage event;
        try {
            event = jsonMapper.readValue(message.getBody(), PetEventMessage.class);
        } catch (JacksonException ex) {
            throw new AmqpRejectAndDontRequeueException("Unreadable pet event", ex);
        }
        if (event == null || !event.isValid()) {
            throw new AmqpRejectAndDontRequeueException("Not a valid pet.created / pet.updated / pet.deleted event: "
                + event);
        }
        return event;
    }
}
