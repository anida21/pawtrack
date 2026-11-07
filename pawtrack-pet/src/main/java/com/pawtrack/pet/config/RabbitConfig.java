package com.pawtrack.pet.config;

import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the shared {@code pawtrack.events} topic exchange (durable) and sends messages as JSON.
 * Consumer side (events.md): queue {@code pet.household-events} bound to {@code household.deleted};
 * messages it rejects go through {@code pawtrack.events.dlx} to {@code pet.household-events.dlq}.
 */
@Configuration
public class RabbitConfig {

    static final String HOUSEHOLD_DELETED = "household.deleted";
    static final String REPLAY_REQUESTED = "replay.requested";
    static final String MEMBER_REMOVED = "household.member.removed";

    @Bean
    TopicExchange eventsExchange(PawtrackProperties properties) {
        return new TopicExchange(properties.events().exchange(), true, false);
    }

    /**
     * JSON only for sending. Deliberately not a {@code MessageConverter} bean: Boot would also put it on the
     * listener containers, where it deserializes by the sender's {@code __TypeId__} header (a class that only
     * exists in the sending service) and dead-letters every real cross-service event.
     * Consumers parse the raw {@code Message} body themselves.
     */
    @Bean
    RabbitTemplateCustomizer jsonForSending() {
        return template -> template.setMessageConverter(new JacksonJsonMessageConverter());
    }

    // ---- consumer: household events ----------------------------------------------------------------------

    @Bean
    DirectExchange deadLetterExchange(PawtrackProperties properties) {
        return new DirectExchange(properties.events().deadLetterExchange(), true, false);
    }

    @Bean
    Queue householdEventsQueue(PawtrackProperties properties) {
        PawtrackProperties.Events events = properties.events();
        return QueueBuilder.durable(events.householdQueue())
            .deadLetterExchange(events.deadLetterExchange())
            .deadLetterRoutingKey(dlqName(events))
            .build();
    }

    @Bean
    Queue householdEventsDeadLetterQueue(PawtrackProperties properties) {
        return QueueBuilder.durable(dlqName(properties.events())).build();
    }

    @Bean
    Binding householdDeletedBinding(Queue householdEventsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(householdEventsQueue).to(eventsExchange).with(HOUSEHOLD_DELETED);
    }

    @Bean
    Binding householdEventsDeadLetterBinding(Queue householdEventsDeadLetterQueue, DirectExchange deadLetterExchange,
                                             PawtrackProperties properties) {
        return BindingBuilder.bind(householdEventsDeadLetterQueue).to(deadLetterExchange)
            .with(dlqName(properties.events()));
    }

    static String dlqName(PawtrackProperties.Events events) {
        return events.householdQueue() + ".dlq";
    }

    // ---- membership cache eviction ---------------------------------------------------------------------------

    /**
     * Every pet instance keeps its own membership cache, so every instance needs {@code household.member.removed}:
     * a per-instance exclusive, auto-delete queue (not a shared durable one, which would reach only one instance).
     * Nothing is lost if an instance is down: a fresh instance starts with an empty cache.
     */
    @Bean
    Queue membershipChangesQueue() {
        return new AnonymousQueue();
    }

    @Bean
    Binding memberRemovedBinding(Queue membershipChangesQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(membershipChangesQueue).to(eventsExchange).with(MEMBER_REMOVED);
    }

    // ---- consumer: replay requests (backfill, events.md) ---------------------------------------------------

    @Bean
    Declarables replayRequestQueue(PawtrackProperties properties, TopicExchange eventsExchange,
                                   DirectExchange deadLetterExchange) {
        String queueName = properties.events().replayQueue();
        String replayDlq = queueName + ".dlq";
        Queue queue = QueueBuilder.durable(queueName)
            .deadLetterExchange(deadLetterExchange.getName())
            .deadLetterRoutingKey(replayDlq)
            .build();
        Queue dlq = QueueBuilder.durable(replayDlq).build();
        return new Declarables(queue, dlq,
            BindingBuilder.bind(queue).to(eventsExchange).with(REPLAY_REQUESTED),
            BindingBuilder.bind(dlq).to(deadLetterExchange).with(replayDlq));
    }
}
