package com.fincity.security.configuration;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

import com.fincity.saas.commons.jooq.configuration.AbstractJooqBaseConfiguration;
import com.fincity.saas.commons.security.ISecurityConfiguration;
import com.fincity.security.service.AuthenticationService;
import com.fincity.security.service.SecurityMessageResourceService;

@Configuration
public class SecurityConfiguration extends AbstractJooqBaseConfiguration implements ISecurityConfiguration {

	@Autowired
	protected SecurityMessageResourceService messageResourceService;

	@Override
	@PostConstruct
	public void initialize() {
		super.initialize(messageResourceService);
	}

	@Bean
	SecurityWebFilterChain filterChain(ServerHttpSecurity http, AuthenticationService authService) {
		return this.springSecurityFilterChain(http, authService, "/api/security/authenticate",
		        "/api/security/clients/internal/isBeingManaged", "/api/security/clients/internal/getClientNAppCode");
	}

}
