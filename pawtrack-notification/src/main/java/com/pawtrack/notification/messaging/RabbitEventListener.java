package com.pawtrack.notification.messaging;

import com.pawtrack.notification.config.RabbitConfig;
import com.pawtrack.notification.service.EventHandlingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumes the three notification queues. Parsing happens here, the work in {@link EventHandlingService}
 * (one transaction per event). A failing event is retried (spring.rabbitmq.listener.simple.retry),
 * then rejected without requeue, so RabbitMQ moves it to {@code <queue>.dlq}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitEventListener {

    private final JsonMapper jsonMapper;
    private final EventHandlingService eventHandling;

    @RabbitListener(queues = RabbitConfig.IDENTITY_QUEUE)
    public void onIdentityEvent(Message message) {
        handle(message);
    }

    @RabbitListener(queues = RabbitConfig.PET_QUEUE)
    public void onPetEvent(Message message) {
        handle(message);
    }

    @RabbitListener(queues = RabbitConfig.HEALTH_QUEUE)
    public void onHealthEvent(Message message) {
        handle(message);
    }

    private void handle(Message message) {
        IncomingEvent envelope = jsonMapper.readValue(message.getBody(), IncomingEvent.class);
        if (envelope.eventId() == null || envelope.type() == null) {
            throw new IllegalArgumentException("Event without eventId/type: " + new String(message.getBody()));
        }
        Class<? extends ConsumedEvent> dataType = ConsumedEvent.TYPES.get(envelope.type());
        if (dataType == null) {
            log.debug("Ignoring event {} ({})", envelope.type(), envelope.eventId());
            return;
        }
        ConsumedEvent data = jsonMapper.treeToValue(envelope.data(), dataType);
        eventHandling.handle(envelope.eventId(), envelope.type(), data);
    }
}
