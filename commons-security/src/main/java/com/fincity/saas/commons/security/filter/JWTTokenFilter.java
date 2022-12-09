package com.fincity.saas.commons.security.filter;

import java.util.List;

import javax.naming.AuthenticationException;

import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.common.security.jwt.ContextAuthentication;
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

		List<String> clientCode = request.getHeaders()
		        .get("clientCode");
		final String cc = clientCode == null || clientCode.isEmpty() ? null : clientCode.get(0);

		return

		FlatMapUtil.flatMapMonoWithNull(

		        () -> !bearerToken.isBlank() ? this.authService.getAuthentication(isBasic, bearerToken, request)
		                : Mono.empty(),

		        ca ->
				{

			        if (ca == null)
				        return Mono.empty();

			        if (cc != null && !cc.equals(((ContextAuthentication) ca).getLoggedInFromClientCode()))
				        return Mono.error(new AuthenticationException("Trying to access with a cross site token."));

			        return Mono.just(
			                ReactiveSecurityContextHolder.withSecurityContext(Mono.just(new SecurityContextImpl(ca))));
		        },

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
