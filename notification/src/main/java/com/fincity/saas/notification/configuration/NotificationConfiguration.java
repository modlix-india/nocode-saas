package com.fincity.saas.notification.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListenerConfigurer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistrar;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.configuration.AbstractBaseConfiguration;
import com.fincity.saas.commons.mq.configuration.IMQConfiguration;
import com.fincity.saas.commons.security.ISecurityConfiguration;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.notification.service.NotificationMessageResourceService;

import jakarta.annotation.PostConstruct;

@Configuration
public class NotificationConfiguration extends AbstractBaseConfiguration implements ISecurityConfiguration, IMQConfiguration, RabbitListenerConfigurer {

	private final NotificationMessageResourceService messageService;

	@Value("${spring.r2dbc.url}")
	private String url;

	@Value("${spring.r2dbc.username}")
	private String username;

	@Value("${spring.r2dbc.password}")
	private String password;

	protected NotificationConfiguration(NotificationMessageResourceService messageService, ObjectMapper objectMapper) {
		super(objectMapper);
		this.messageService = messageService;
	}

	@PostConstruct
	@Override
	public void initialize() {

		super.initialize();
		Logger log = LoggerFactory.getLogger(FlatMapUtil.class);
		FlatMapUtil.setLogConsumer(signal -> LogUtil.logIfDebugKey(signal, (name, v) -> {
			if (name != null)
				log.debug("{} - {}", name, v);
			else
				log.debug(v);
		}));
	}

	@Override
	public void configureRabbitListeners(RabbitListenerEndpointRegistrar rabbitListenerEndpointRegistrar) {

	}
}
