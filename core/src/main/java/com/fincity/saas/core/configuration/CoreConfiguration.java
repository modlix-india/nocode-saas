package com.fincity.saas.core.configuration;

import javax.annotation.PostConstruct;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

import com.fincity.saas.commons.mongo.configuration.AbstractMongoConfiguration;
import com.fincity.saas.commons.mongo.jackson.KIRuntimeSerializationModule;
import com.fincity.saas.commons.security.ISecurityConfiguration;
import com.fincity.saas.commons.security.service.FeignAuthenticationService;
import com.fincity.saas.core.service.CoreMessageResourceService;

import io.r2dbc.spi.ConnectionFactory;

@Configuration
public class CoreConfiguration extends AbstractMongoConfiguration implements ISecurityConfiguration {
	
	@Autowired
	private CoreMessageResourceService messageService;

	@PostConstruct
	@Override
	public void initialize() {

		super.initialize();
		this.objectMapper.registerModule(new KIRuntimeSerializationModule());
		this.objectMapper.registerModule(
		        new com.fincity.saas.commons.jooq.jackson.UnsignedNumbersSerializationModule(messageService));
	}

	@Bean
	DSLContext context(ConnectionFactory factory) {
		return DSL.using(factory);
	}

	@Bean
	SecurityWebFilterChain filterChain(ServerHttpSecurity http, FeignAuthenticationService authService) {
		return this.springSecurityFilterChain(http, authService, "/api/core/function/**");
	}
}
