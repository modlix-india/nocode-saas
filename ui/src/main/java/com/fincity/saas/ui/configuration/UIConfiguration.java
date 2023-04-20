package com.fincity.saas.ui.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

import com.fincity.saas.commons.mongo.configuration.AbstractMongoConfiguration;
import com.fincity.saas.commons.mongo.jackson.KIRuntimeSerializationModule;
import com.fincity.saas.commons.security.ISecurityConfiguration;
import com.fincity.saas.commons.security.service.FeignAuthenticationService;

import jakarta.annotation.PostConstruct;

@Configuration
public class UIConfiguration extends AbstractMongoConfiguration implements ISecurityConfiguration {

	@Override
	@PostConstruct
	public void initialize() {
		super.initialize();
		this.objectMapper.registerModule(new KIRuntimeSerializationModule());
	}

	@Bean
	SecurityWebFilterChain filterChain(ServerHttpSecurity http, FeignAuthenticationService authService) {
		return this.springSecurityFilterChain(http, authService, "/**");
	}
}
