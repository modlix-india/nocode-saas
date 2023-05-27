package com.fincity.security.configuration;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fincity.saas.commons.jackson.TupleSerializationModule;
import com.fincity.saas.commons.jooq.configuration.AbstractJooqBaseConfiguration;
import com.fincity.saas.commons.jooq.jackson.UnsignedNumbersSerializationModule;
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
	MappingJackson2XmlHttpMessageConverter jackson2XmlHttpMessageConverter() {

		return this.jackson2XmlHttpMessageConverter(

		        new UnsignedNumbersSerializationModule(messageResourceService),

		        new TupleSerializationModule(),

		        new JavaTimeModule());
	}

	@Bean
	SecurityWebFilterChain filterChain(ServerHttpSecurity http, AuthenticationService authService) {
		return this.springSecurityFilterChain(http, authService, this.objectMapper, "/actuator/**",
		        "/api/security/authenticate", "/api/security/verifyToken", "/api/security/clients/internal/**",
		        "/api/security/applications/internal/**", "/api/security/internal/securityContextAuthentication",
		        "/api/security/users/findUserClients", "/api/security/clients/register");
	}

}
