package com.pawtrack.pet.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Entry point for services. Must be called inside a transaction: the event is handed to
 * {@link RabbitEventRelay} and only sent to RabbitMQ once that transaction has committed.
 */
@Component
@RequiredArgsConstructor
public class EventPublisher {

    private final ApplicationEventPublisher applicationEvents;

    public void publish(PetEvent event) {
        applicationEvents.publishEvent(EventEnvelope.wrap(event));
    }
}
