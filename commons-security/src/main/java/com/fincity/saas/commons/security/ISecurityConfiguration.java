package com.fincity.saas.commons.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

import com.fincity.saas.commons.security.filter.JWTTokenFilter;
import com.fincity.saas.commons.security.service.IAuthenticationService;

public interface ISecurityConfiguration {

	default SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http, IAuthenticationService authService,
	        String... exclusionList) {
		http.csrf()
		        .disable()
		        .cors()
		        .disable()
		        .authorizeExchange(exchanges ->
				{
			        var pathMatchers = exchanges.pathMatchers(HttpMethod.OPTIONS, "/**")
			                .permitAll()
			                .pathMatchers("**/internal/**")
			                .permitAll()
			                .pathMatchers("/actuator/**")
			                .permitAll();

			        for (String exclusion : exclusionList) {
				        pathMatchers.pathMatchers(exclusion)
				                .permitAll();
			        }

			        pathMatchers.pathMatchers("/api/**")
			                .authenticated();
		        })
		        .addFilterAt(new JWTTokenFilter(authService), SecurityWebFiltersOrder.HTTP_BASIC)
		        .formLogin()
		        .disable()
		        .logout()
		        .disable();

		return http.build();
	}
}
