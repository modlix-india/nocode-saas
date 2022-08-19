package com.fincity.security.configuration;

import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.fincity.security.service.AuthenticationService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class JWTTokenFilter implements WebFilter {

	private final AuthenticationService authService;

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {

		ServerHttpRequest request = exchange.getRequest();

		String bearerToken = request.getHeaders()
		        .getFirst(HttpHeaders.AUTHORIZATION);

		if (bearerToken == null || bearerToken.isBlank()) {
			HttpCookie cookie = request.getCookies()
			        .getFirst(HttpHeaders.AUTHORIZATION);
			if (cookie != null)
				bearerToken = cookie.getValue();
		}

		boolean isBasic = false;
		if (bearerToken != null) {

			bearerToken = bearerToken.trim();

			if (bearerToken.startsWith("Bearer ")) {
				bearerToken = bearerToken.substring(7);
			} else if (bearerToken.startsWith("basic ")) {
				isBasic = true;
				bearerToken = bearerToken.substring(6);
			}
		}

		if (bearerToken != null && !bearerToken.isBlank()) {

			Mono<Authentication> authentication = this.authService.getAuthentication(isBasic, bearerToken, request);
			
			return chain.filter(exchange)
			        .contextWrite(ReactiveSecurityContextHolder
			                .withSecurityContext(authentication.map(SecurityContextImpl::new)));
		}
		return chain.filter(exchange);
	}
}
