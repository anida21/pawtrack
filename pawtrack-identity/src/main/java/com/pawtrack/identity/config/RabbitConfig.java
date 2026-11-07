package com.pawtrack.identity.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
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

    @Bean
    MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
