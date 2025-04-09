package com.fincity.saas.commons.jooq.configuration;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.fincity.saas.commons.configuration.AbstractBaseConfiguration;
import com.fincity.saas.commons.configuration.service.AbstractMessageService;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.ConnectionFactoryOptions.Builder;
import lombok.Getter;

@Getter
public abstract class AbstractJooqBaseConfiguration extends AbstractBaseConfiguration {

  @Value("${spring.r2dbc.url}")
  protected String url;

  @Value("${spring.r2dbc.username}")
  protected String username;

  @Value("${spring.r2dbc.password}")
	protected String password;

	protected AbstractJooqBaseConfiguration(ObjectMapper objectMapper) {
		super(objectMapper);
	}

	public void initialize(AbstractMessageService messageResourceService) {
		super.initialize();
		this.objectMapper.registerModule(
				new com.fincity.saas.commons.jooq.jackson.UnsignedNumbersSerializationModule(messageResourceService));
	}

	@Bean
	DSLContext context() {

		Builder props = ConnectionFactoryOptions.parse(url)
				.mutate();
		ConnectionFactory factory = ConnectionFactories.get(props
				.option(ConnectionFactoryOptions.DRIVER, "pool")
				.option(ConnectionFactoryOptions.PROTOCOL, "mysql")
				.option(ConnectionFactoryOptions.USER, username)
				.option(ConnectionFactoryOptions.PASSWORD, password)
				.build());
		return DSL.using(new ConnectionPool(ConnectionPoolConfiguration.builder(factory)
				.build()));
	}
}
