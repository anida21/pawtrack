package com.pawtrack.health.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.pawtrack.health.config.PawtrackProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Backfill (events.md): on startup asks pet to republish every pet as {@code pet.updated}, so the local pet copy
 * also covers pets created before this service (or its database) existed. pet.updated is an upsert here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReplayRequester {

    static final String TYPE = "replay.requested";
    static final List<String> TYPES = List.of(PetEventMessage.PET_UPDATED);

    private final RabbitTemplate rabbitTemplate;
    private final JsonMapper jsonMapper;
    private final PawtrackProperties properties;
    private final Clock clock;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (properties.events().replayOnStartup()) {
            requestReplay();
        }
    }

    public void requestReplay() {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", UUID.randomUUID());
        envelope.put("type", TYPE);
        envelope.put("occurredAt", clock.instant().toString());
        envelope.put("producer", "pawtrack-health");
        envelope.put("data", Map.of("requestedBy", "pawtrack-health", "types", TYPES));
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setContentEncoding(StandardCharsets.UTF_8.name());
        try {
            rabbitTemplate.send(properties.events().exchange(), TYPE,
                MessageBuilder.withBody(jsonMapper.writeValueAsBytes(envelope)).andProperties(props).build());
            log.info("Requested replay of {}", TYPES);
        } catch (AmqpException ex) {
            // Not fatal: live events still arrive; the next restart asks again.
            log.warn("Could not request replay", ex);
        }
    }
}
