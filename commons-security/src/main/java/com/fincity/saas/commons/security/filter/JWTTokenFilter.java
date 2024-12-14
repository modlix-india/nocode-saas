package com.fincity.saas.commons.security.filter;

import java.util.List;

import javax.naming.AuthenticationException;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.service.IAuthenticationService;
import com.fincity.saas.commons.security.util.ServerHttpRequestUtil;
import com.fincity.saas.commons.util.LogUtil;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;

@RequiredArgsConstructor
public class JWTTokenFilter implements WebFilter {

	private final IAuthenticationService authService;
	private final ObjectMapper mapper;

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {

		ServerHttpRequest request = exchange.getRequest();
		Tuple2<Boolean, String> tuple = ServerHttpRequestUtil.extractBasicNBearerToken(request);

		boolean isBasic = tuple.getT1();
		String bearerToken = tuple.getT2();

		List<String> clientCode = request.getHeaders()
				.get("clientCode");
		List<String> appCode = request.getHeaders()
				.get("appCode");
		final String cc = clientCode == null || clientCode.isEmpty() ? null : clientCode.get(0);
		final String ac = appCode == null || appCode.isEmpty() ? null : appCode.get(0);

		final List<String> debugCode = request.getHeaders()
				.get(LogUtil.DEBUG_KEY);
		final String dc = debugCode == null || debugCode.isEmpty() ? null : debugCode.get(0);

		var mono = FlatMapUtil.flatMapMono(

				() -> this.authService.getAuthentication(isBasic, bearerToken, cc, ac, request),

				ca -> {

					if (cc != null && !"SYSTEM".equals(cc) && ca.isAuthenticated()
							&& !cc.equals(((ContextAuthentication) ca).getLoggedInFromClientCode()))
						return Mono.error(new AuthenticationException("Trying to access with a cross site token."));

					ContextAuthentication newCA = mapper.convertValue(ca, ContextAuthentication.class)
							.setUrlAppCode(ac)
							.setUrlClientCode(cc);

					return chain.filter(exchange)
							.contextWrite(ReactiveSecurityContextHolder
									.withSecurityContext(Mono.just(new SecurityContextImpl(newCA))));
				});
		if (dc == null)
			return mono;
		return mono.contextWrite(Context.of(LogUtil.DEBUG_KEY, dc));
	}

}
