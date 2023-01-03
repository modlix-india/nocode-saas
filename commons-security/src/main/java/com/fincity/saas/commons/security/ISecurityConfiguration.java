package com.fincity.saas.commons.security;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity.AuthorizeExchangeSpec;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.HttpBasicServerAuthenticationEntryPoint;
import org.springframework.web.server.ServerWebExchange;

import com.fincity.saas.commons.security.filter.JWTTokenFilter;
import com.fincity.saas.commons.security.service.IAuthenticationService;

import reactor.core.publisher.Mono;

public interface ISecurityConfiguration {
	default SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http,
	        IAuthenticationService authService, String... exclusionList) {
		AuthorizeExchangeSpec permits = http.csrf()
		        .disable()
		        .cors()
		        .disable()
		        .authorizeExchange()
		        .pathMatchers(HttpMethod.OPTIONS)
		        .permitAll()
		        .pathMatchers("**/internal/**")
		        .permitAll()
		        .pathMatchers("/actuator/**")
		        .permitAll();

		if (exclusionList != null && exclusionList.length != 0)
			permits = permits.pathMatchers(exclusionList)
			        .permitAll();

		permits.anyExchange()
		        .authenticated()
		        .and()
		        .addFilterAt(new JWTTokenFilter(authService), SecurityWebFiltersOrder.HTTP_BASIC)
		        .httpBasic()
		        .authenticationEntryPoint(new HttpBasicServerAuthenticationEntryPoint() {
			        @Override
			        public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
				        return Mono.fromRunnable(() -> {
					        ServerHttpResponse response = exchange.getResponse();
					        response.setStatusCode(HttpStatus.UNAUTHORIZED);
					        response.getHeaders()
					                .remove("WWW-Authenticate");
				        });
			        }
		        })
		        .and()
		        .formLogin()
		        .disable()
		        .logout()
		        .disable();
		return http.build();
	}
}
