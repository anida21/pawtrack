package com.pawtrack.identity.config;

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
}
