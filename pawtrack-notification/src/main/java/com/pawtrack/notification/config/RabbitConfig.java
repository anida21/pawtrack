package com.pawtrack.notification.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the shared {@code pawtrack.events} topic exchange, the dead-letter exchange and the queues
 * this service consumes (see {@code contracts/events.md}): one queue per producer, each with its own DLQ.
 */
@Configuration
public class RabbitConfig {

    public static final String IDENTITY_QUEUE = "notification.identity-events";
    public static final String PET_QUEUE = "notification.pet-events";
    public static final String HEALTH_QUEUE = "notification.health-events";

    /** Routing keys (= event types) each queue is bound with. */
    static final Map<String, List<String>> BINDINGS = Map.of(
        IDENTITY_QUEUE, List.of("household.#", "user.updated"),
        PET_QUEUE, List.of("pet.created", "pet.updated", "pet.deleted"),
        HEALTH_QUEUE, List.of("health.vaccination.recorded", "health.medication.scheduled",
            "health.medication.cancelled"));

    @Bean
    TopicExchange eventsExchange(PawtrackProperties properties) {
        return new TopicExchange(properties.events().exchange(), true, false);
    }

    @Bean
    DirectExchange deadLetterExchange(PawtrackProperties properties) {
        return new DirectExchange(properties.events().deadLetterExchange(), true, false);
    }

    @Bean
    Declarables consumerQueues(TopicExchange eventsExchange, DirectExchange deadLetterExchange) {
        List<Declarable> declarables = new ArrayList<>();
        BINDINGS.forEach((queueName, routingKeys) -> {
            String dlqName = queueName + ".dlq";
            Queue queue = QueueBuilder.durable(queueName)
                .deadLetterExchange(deadLetterExchange.getName())
                .deadLetterRoutingKey(dlqName)
                .build();
            Queue dlq = QueueBuilder.durable(dlqName).build();
            declarables.add(queue);
            declarables.add(dlq);
            declarables.add(BindingBuilder.bind(dlq).to(deadLetterExchange).with(dlqName));
            for (String key : routingKeys) {
                Binding binding = BindingBuilder.bind(queue).to(eventsExchange).with(key);
                declarables.add(binding);
            }
        });
        return new Declarables(declarables);
    }
}
