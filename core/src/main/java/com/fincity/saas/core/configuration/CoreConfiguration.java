package com.fincity.saas.core.configuration;

import javax.annotation.PostConstruct;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

import com.fincity.saas.commons.mongo.configuration.AbstractMongoConfiguration;
import com.fincity.saas.commons.mongo.jackson.KIRuntimeSerializationModule;
import com.fincity.saas.commons.security.ISecurityConfiguration;
import com.fincity.saas.commons.security.service.FeignAuthenticationService;
import com.fincity.saas.core.service.CoreMessageResourceService;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.ConnectionFactoryOptions.Builder;

@Configuration
public class CoreConfiguration extends AbstractMongoConfiguration implements ISecurityConfiguration {

	@Autowired
	private CoreMessageResourceService messageService;

	@Value("${spring.r2dbc.url}")
	private String url;

	@Value("${spring.r2dbc.username}")
	private String username;

	@Value("${spring.r2dbc.password}")
	private String password;

	@PostConstruct
	@Override
	public void initialize() {

		super.initialize();
		this.objectMapper.registerModule(new KIRuntimeSerializationModule());
		this.objectMapper.registerModule(
		        new com.fincity.saas.commons.jooq.jackson.UnsignedNumbersSerializationModule(messageService));
	}

	@Bean
	DSLContext context() {

		Builder props = ConnectionFactoryOptions.parse(url)
		        .mutate();
		ConnectionFactory factory = ConnectionFactories.get(props.option(ConnectionFactoryOptions.DRIVER, "pool")
		        .option(ConnectionFactoryOptions.PROTOCOL, "mysql")
		        .option(ConnectionFactoryOptions.USER, username)
		        .option(ConnectionFactoryOptions.PASSWORD, password)
		        .build());
		return DSL.using(new ConnectionPool(ConnectionPoolConfiguration.builder(factory)
		        .build()));
	}

	@Bean
	SecurityWebFilterChain filterChain(ServerHttpSecurity http, FeignAuthenticationService authService) {
		return this.springSecurityFilterChain(http, authService, this.objectMapper, "/api/core/function/**");
	}
}
