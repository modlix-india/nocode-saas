package com.fincity.saas.commons.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.security.filter.JWTTokenFilter;
import com.fincity.saas.commons.security.service.IAuthenticationService;

public interface ISecurityConfiguration {
	default SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http,
			IAuthenticationService authService, ObjectMapper om, String... exclusionList) {

		return this.springSecurityFilterChain(http, authService, om, (ServerWebExchangeMatcher) null, exclusionList);
	}

	default SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http,
			IAuthenticationService authService, ObjectMapper om, ServerWebExchangeMatcher matcher,
			String... exclusionList) {

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		CorsConfiguration config = new CorsConfiguration();
		config.addAllowedOrigin("*");
		config.addAllowedHeader("*");
		config.addAllowedMethod("*");
		source.registerCorsConfiguration("/**", config);

		http
				.csrf(ServerHttpSecurity.CsrfSpec::disable)
				.cors(cors -> cors.configurationSource(source))
				.authorizeExchange(authorize -> {
					authorize
							.pathMatchers(HttpMethod.OPTIONS).permitAll()
							.pathMatchers("(.*internal.*)").permitAll()
							.pathMatchers("/actuator/**").permitAll();
					if (exclusionList != null && exclusionList.length != 0)
						authorize.pathMatchers(exclusionList).permitAll();

					authorize.anyExchange().authenticated();
				})
				.headers(headers -> {
					if (matcher == null)
						return;
					headers
							.frameOptions(frameOptions -> frameOptions
									.mode(XFrameOptionsServerHttpHeadersWriter.Mode.SAMEORIGIN))
							.contentSecurityPolicy(csp -> csp.policyDirectives("frame-ancestors 'self'"));
				})
				.addFilterAt(new JWTTokenFilter(authService, om), SecurityWebFiltersOrder.HTTP_BASIC)
				.formLogin(ServerHttpSecurity.FormLoginSpec::disable)
				.logout(ServerHttpSecurity.LogoutSpec::disable);

		return http.build();
	}
}
