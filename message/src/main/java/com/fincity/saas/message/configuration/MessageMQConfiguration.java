package com.fincity.saas.message.configuration;

import com.fincity.saas.commons.mq.configuration.IMQConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ for raising core events.
 *
 * <p>This service only publishes, through {@code EventCreationService}, for numbers and calls owned
 * by {@code core}. It declares no listener of its own.
 *
 * <p>The JSON converter from {@link IMQConfiguration} is the part that matters. Spring Boot hands the
 * only {@code MessageConverter} bean to the {@code RabbitTemplate}, and without it the template falls
 * back to Java serialisation, which core's {@code EventsQueListener} cannot read: it consumes with
 * the JSON converter, so every event would be dropped on arrival.
 */
@Configuration
public class MessageMQConfiguration implements IMQConfiguration {}
