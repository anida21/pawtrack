package com.pawtrack.identity.config;

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
 */
@Configuration
public class RabbitConfig {

    public static final String REPLAY_REQUESTED = "replay.requested";

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

    // ---- consumer: replay requests (backfill, events.md) ---------------------------------------------------

    @Bean
    DirectExchange deadLetterExchange(PawtrackProperties properties) {
        return new DirectExchange(properties.events().deadLetterExchange(), true, false);
    }

    @Bean
    Declarables replayRequestQueue(PawtrackProperties properties, TopicExchange eventsExchange,
                                   DirectExchange deadLetterExchange) {
        String queueName = properties.events().replayQueue();
        String dlqName = queueName + ".dlq";
        Queue queue = QueueBuilder.durable(queueName)
            .deadLetterExchange(deadLetterExchange.getName())
            .deadLetterRoutingKey(dlqName)
            .build();
        Queue dlq = QueueBuilder.durable(dlqName).build();
        return new Declarables(queue, dlq,
            BindingBuilder.bind(queue).to(eventsExchange).with(REPLAY_REQUESTED),
            BindingBuilder.bind(dlq).to(deadLetterExchange).with(dlqName));
    }
}
