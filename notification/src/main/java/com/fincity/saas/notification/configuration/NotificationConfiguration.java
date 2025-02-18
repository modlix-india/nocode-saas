package com.fincity.saas.notification.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListenerConfigurer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistrar;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.configuration.AbstractJooqBaseConfiguration;
import com.fincity.saas.commons.mq.configuration.IMQConfiguration;
import com.fincity.saas.commons.security.ISecurityConfiguration;
import com.fincity.saas.commons.security.service.FeignAuthenticationService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.notification.enums.PreferenceLevel;
import com.fincity.saas.notification.service.NotificationMessageResourceService;
import com.fincity.saas.notification.util.converter.EnumToStringConverter;
import com.fincity.saas.notification.util.converter.StringToEnumConverter;

import jakarta.annotation.PostConstruct;

@Configuration
public class NotificationConfiguration extends AbstractJooqBaseConfiguration
		implements ISecurityConfiguration, IMQConfiguration, RabbitListenerConfigurer {

	private final NotificationMessageResourceService messageService;

	protected NotificationConfiguration(NotificationMessageResourceService messageService, ObjectMapper objectMapper) {
		super(objectMapper);
		this.messageService = messageService;
	}

	@PostConstruct
	@Override
	public void initialize() {

		super.initialize();
		this.objectMapper.registerModule(
				new com.fincity.saas.commons.jooq.jackson.UnsignedNumbersSerializationModule(messageService));
		Logger log = LoggerFactory.getLogger(FlatMapUtil.class);
		FlatMapUtil.setLogConsumer(signal -> LogUtil.logIfDebugKey(signal, (name, v) -> {
			if (name != null)
				log.debug("{} - {}", name, v);
			else
				log.debug(v);
		}));
	}

	@Bean
	public SecurityWebFilterChain filterChain(ServerHttpSecurity http, FeignAuthenticationService authService) {
		return this.springSecurityFilterChain(http, authService, this.objectMapper);
	}

	@Override
	public void configureRabbitListeners(RabbitListenerEndpointRegistrar rabbitListenerEndpointRegistrar) {

	}

	@Override
	public void addFormatters(FormatterRegistry registry) {
		registry.addConverter(new StringToEnumConverter<>(PreferenceLevel.class));
		registry.addConverter(new EnumToStringConverter<>());
	}

}
