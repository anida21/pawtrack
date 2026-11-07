package com.pawtrack.pet.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
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

    @Bean
    TopicExchange eventsExchange(PawtrackProperties properties) {
        return new TopicExchange(properties.events().exchange(), true, false);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
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
}
