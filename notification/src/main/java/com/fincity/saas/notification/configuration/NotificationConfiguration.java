package com.fincity.saas.notification.configuration;

import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.configuration.AbstractBaseConfiguration;
import com.fincity.saas.commons.mq.configuration.IMQConfiguration;
import com.fincity.saas.commons.security.ISecurityConfiguration;

@Configuration
public class NotificationConfiguration extends AbstractBaseConfiguration implements ISecurityConfiguration, IMQConfiguration {

	protected NotificationConfiguration(ObjectMapper objectMapper) {
		super(objectMapper);
	}
}
