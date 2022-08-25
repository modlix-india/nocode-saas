package com.fincity.saas.commons.jooq.configuration;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.context.annotation.Bean;

import com.fincity.saas.commons.configuration.AbstractBaseConfiguration;
import com.fincity.saas.commons.configuration.service.AbstractMessageService;
import com.fincity.saas.commons.jooq.jackson.UnsignedNumbersSerializationModule;

import io.r2dbc.spi.ConnectionFactory;

public class AbstractJooqBaseConfiguration extends AbstractBaseConfiguration {

	
	public void initialize(AbstractMessageService messageResourceService) {
		super.initialize();
		this.objectMapper.registerModule(new UnsignedNumbersSerializationModule(messageResourceService));
	}
	
	@Bean
	DSLContext context(ConnectionFactory factory) {
		return DSL.using(factory);
	}
}
