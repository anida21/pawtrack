package com.pawtrack.health.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
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
 * Consumer side (events.md): queue {@code health.pet-events} bound to {@code pet.created} and {@code pet.deleted};
 * messages it rejects go through {@code pawtrack.events.dlx} to {@code health.pet-events.dlq}.
 */
@Configuration
public class RabbitConfig {

    static final String PET_CREATED = "pet.created";
    static final String PET_DELETED = "pet.deleted";

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

    // ---- consumer: pet events ----------------------------------------------------------------------------

    @Bean
    DirectExchange deadLetterExchange(PawtrackProperties properties) {
        return new DirectExchange(properties.events().deadLetterExchange(), true, false);
    }

    @Bean
    Queue petEventsQueue(PawtrackProperties properties) {
        PawtrackProperties.Events events = properties.events();
        return QueueBuilder.durable(events.petQueue())
            .deadLetterExchange(events.deadLetterExchange())
            .deadLetterRoutingKey(dlqName(events))
            .build();
    }

    @Bean
    Queue petEventsDeadLetterQueue(PawtrackProperties properties) {
        return QueueBuilder.durable(dlqName(properties.events())).build();
    }

    @Bean
    Binding petCreatedBinding(Queue petEventsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(petEventsQueue).to(eventsExchange).with(PET_CREATED);
    }

    @Bean
    Binding petDeletedBinding(Queue petEventsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(petEventsQueue).to(eventsExchange).with(PET_DELETED);
    }

    @Bean
    Binding petEventsDeadLetterBinding(Queue petEventsDeadLetterQueue, DirectExchange deadLetterExchange,
                                       PawtrackProperties properties) {
        return BindingBuilder.bind(petEventsDeadLetterQueue).to(deadLetterExchange)
            .with(dlqName(properties.events()));
    }

    static String dlqName(PawtrackProperties.Events events) {
        return events.petQueue() + ".dlq";
    }
}
