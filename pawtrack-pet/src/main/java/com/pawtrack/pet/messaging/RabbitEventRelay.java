package com.pawtrack.pet.messaging;

import com.pawtrack.pet.config.PawtrackProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends events to the {@code pawtrack.events} topic exchange after the business transaction commits,
 * so consumers never see changes that were rolled back. Routing key = event type.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitEventRelay {

    private final RabbitTemplate rabbitTemplate;
    private final PawtrackProperties properties;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void relay(EventEnvelope envelope) {
        try {
            rabbitTemplate.convertAndSend(properties.events().exchange(), envelope.type(), envelope);
        } catch (AmqpException ex) {
            // The data change is already committed; losing the event is logged rather than failing the request.
            log.error("Could not publish event {} ({})", envelope.type(), envelope.eventId(), ex);
        }
    }
}
