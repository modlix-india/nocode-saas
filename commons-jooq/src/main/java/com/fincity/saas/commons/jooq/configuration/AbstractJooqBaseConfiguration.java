package com.fincity.saas.commons.jooq.configuration;

import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.configuration.AbstractBaseConfiguration;
import com.fincity.saas.commons.configuration.service.AbstractMessageService;

import io.r2dbc.spi.ConnectionFactory;

public abstract class AbstractJooqBaseConfiguration extends AbstractBaseConfiguration {

	@Value("${spring.r2dbc.url}")
	private String url;

	@Value("${spring.r2dbc.username}")
	private String username;

	@Value("${spring.r2dbc.password}")
	private String password;

	private R2dbcConfiguration r2dbcConfiguration;

	protected AbstractJooqBaseConfiguration(ObjectMapper objectMapper) {
		super(objectMapper);
	}

	public void initialize(AbstractMessageService messageResourceService) {
		super.initialize();
		this.objectMapper.registerModule(
				new com.fincity.saas.commons.jooq.jackson.UnsignedNumbersSerializationModule(messageResourceService));
		this.r2dbcConfiguration = new R2dbcConfiguration(url, username, password);
	}

	@Override
	protected void initialize() {
		super.initialize();
		this.r2dbcConfiguration = new R2dbcConfiguration(url, username, password);
	}

	@Bean
	public R2dbcConfiguration r2dbcConfiguration() {
		return this.r2dbcConfiguration;
	}

	@Bean
	public ConnectionFactory connectionFactory() {
		return this.r2dbcConfiguration.connectionFactory();
	}

	@Bean
	public DSLContext dslContext() {
		return this.r2dbcConfiguration.context();
	}
}
