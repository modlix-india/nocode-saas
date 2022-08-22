package com.fincity.security.configuration;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

import com.fincity.saas.commons.jooq.configuration.AbstractJooqBaseConfiguration;
import com.fincity.security.service.AuthenticationService;
import com.fincity.security.service.MessageResourceService;

@Configuration
public class SecurityConfiguration extends AbstractJooqBaseConfiguration {

	@Autowired
	protected MessageResourceService messageResourceService;

	@Override
	@PostConstruct
	public void initialize() {
		super.initialize(messageResourceService);
	}

	@Bean
	SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http, AuthenticationService authService) {
		http.csrf()
		        .disable()
		        .cors()
		        .disable()
		        .authorizeExchange(exchanges -> exchanges.pathMatchers(HttpMethod.OPTIONS, "/**")
		                .permitAll()
		                .pathMatchers("**/internal/**")
		                .permitAll()
		                .pathMatchers("/actuator/**")
		                .permitAll()
		                .pathMatchers("/api/security/authenticate")
		                .permitAll()
		                .pathMatchers("/api/**")
		                .authenticated())
		        .addFilterAt(new JWTTokenFilter(authService), SecurityWebFiltersOrder.HTTP_BASIC)
		        .formLogin()
		        .disable()
		        .logout()
		        .disable();

		return http.build();
	}
}
