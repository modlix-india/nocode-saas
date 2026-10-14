package com.fincity.saas.commons.security;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
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
				.logout(ServerHttpSecurity.LogoutSpec::disable)
				// Basic credentials ("Authorization: Basic ...", header or cookie) are checked by
				// JWTTokenFilter above, not by Spring's httpBasic, so it stays off.
				.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
				// A plain 401 with no challenge. Left unset, ExceptionTranslationWebFilter falls
				// back to HttpBasicServerAuthenticationEntryPoint, which adds
				// `WWW-Authenticate: Basic realm="Realm"` to every 401, and a browser answers that
				// on a credentialed request (EventSource withCredentials, fetch with cookies,
				// document loads) with its native "Sign in" dialog over the app.
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED)));

		return http.build();
	}
}
