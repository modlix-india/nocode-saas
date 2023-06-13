package com.fincity.security.configuration;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

import com.fincity.saas.commons.jooq.configuration.AbstractJooqBaseConfiguration;
import com.fincity.saas.commons.mq.configuration.IMQConfiguration;
import com.fincity.saas.commons.security.ISecurityConfiguration;
import com.fincity.security.service.AuthenticationService;
import com.fincity.security.service.SecurityMessageResourceService;

@Configuration
public class SecurityConfiguration extends AbstractJooqBaseConfiguration
        implements ISecurityConfiguration, IMQConfiguration {

	@Autowired
	protected SecurityMessageResourceService messageResourceService;

	@Override
	@PostConstruct
	public void initialize() {
		super.initialize(messageResourceService);
	}

	@Bean
	SecurityWebFilterChain filterChain(ServerHttpSecurity http, AuthenticationService authService) {
		return this.springSecurityFilterChain(http, authService, this.objectMapper,

		        "/actuator/**",

		        "/api/security/authenticate",

		        "/api/security/verifyToken",

		        "/api/security/clients/internal/**",

		        "/api/security/applications/internal/**",

		        "/api/security/internal/securityContextAuthentication",

		        "/api/security/users/findUserClients",

		        "/api/security/clients/register",

		        "/api/security/users/requestResetPassword");
	}

}
