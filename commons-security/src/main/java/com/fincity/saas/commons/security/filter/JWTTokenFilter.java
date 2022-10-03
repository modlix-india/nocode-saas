package com.fincity.saas.commons.security.filter;

import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.security.service.IAuthenticationService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@RequiredArgsConstructor
public class JWTTokenFilter implements WebFilter {

	private final IAuthenticationService authService;

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {

		ServerHttpRequest request = exchange.getRequest();
		Tuple2<Boolean, String> tuple = this.extractBasicNBearerToken(request);

		boolean isBasic = tuple.getT1();
		String bearerToken = tuple.getT2();

		return

		FlatMapUtil.flatMapMonoWithNull(

		        () -> !bearerToken.isBlank() ? this.authService.getAuthentication(isBasic, bearerToken, request)
		                : Mono.empty(),

		        ca -> ca != null
		                ? Mono.just(ReactiveSecurityContextHolder
		                        .withSecurityContext(Mono.just(new SecurityContextImpl(ca))))
		                : Mono.empty(),

		        (ca, ctx) -> ctx == null ? chain.filter(exchange)
		                : chain.filter(exchange)
		                        .contextWrite(ctx));
	}

	public Tuple2<Boolean, String> extractBasicNBearerToken(ServerHttpRequest request) {

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

		return Tuples.of(isBasic, bearerToken == null ? "" : bearerToken);
	}
}
