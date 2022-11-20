package com.fincity.gateway;

import java.net.URI;
import java.util.WeakHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequest.Builder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.service.CacheService;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

@Component
public class GatewayFilter implements GlobalFilter, Ordered {

	private static final String CACHE_NAME_GATEWAY_URL_CLIENT_APP_CODE = "gatewayClientAppCode";

	@Autowired
	private CacheService cacheService;

	@Autowired
	private IFeignSecurityClient security;

	private WeakHashMap<String, Tuple2<String, String>> urlClientCode = new WeakHashMap<>();

	private static final Logger logger = LoggerFactory.getLogger(GatewayFilter.class);

	@Override
	public int getOrder() {
		return -1;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

		Route route = exchange
		        .getAttribute("org.springframework.cloud.gateway.support.ServerWebExchangeUtils.gatewayRoute");

		if (route != null && route.getId() != null && route.getId()
		        .equals("index"))
			return chain.filter(exchange.mutate()
			        .request(exchange.getRequest()
			                .mutate()
			                .path("/index.html")
			                .build())
			        .build());

		final Tuple3<String, String, String> shpTuple = this.getSchemeHostPort(exchange);

		return this.getClientCode(shpTuple.getT1(), shpTuple.getT2(), shpTuple.getT3())
		        .flatMap(t -> this.rewriteRequest(exchange, t, chain));
	}

	public Mono<Void> rewriteRequest(ServerWebExchange exchange, Tuple2<String, String> finT,
	        GatewayFilterChain chain) {

		String requestPath = exchange.getRequest()
		        .getPath()
		        .toString();
		int apiIndex = requestPath.indexOf("/api/");
		String modifiedRequestPath = requestPath;

		String appCode = finT.getT2();
		String clientCode = finT.getT1();

		if (apiIndex != -1) {

			modifiedRequestPath = requestPath.substring(apiIndex);
			String beforeAPI = requestPath.substring(0, apiIndex);
			Tuple2<String, String> codes = this.urlClientCode.get(beforeAPI);
			if (codes != null) {

				return modifyRequest(exchange, chain, modifiedRequestPath, codes.getT1(), codes.getT2());
			} else {

				String pagePath = beforeAPI.substring(0, beforeAPI.indexOf("/page/") + 1);
				return modifyRequest(exchange, chain, modifiedRequestPath, pagePath.split("/"), clientCode, appCode,
				        beforeAPI);
			}
		}

		int pageIndex = requestPath.indexOf("/page/");
		String beforePage = requestPath.substring(0, pageIndex + 1);

		Tuple2<String, String> codes = this.urlClientCode.get(beforePage);
		if (codes != null) {

			return modifyRequest(exchange, chain, modifiedRequestPath, codes.getT1(), codes.getT2());
		} else {

			return modifyRequest(exchange, chain, modifiedRequestPath, beforePage.split("/"), clientCode, appCode,
			        beforePage);
		}
	}

	private Mono<Void> modifyRequest(ServerWebExchange exchange, GatewayFilterChain chain, String modifiedRequestPath,
	        String[] pageParts, String clientCode, String appCode, String beforeBreakPath) {

		int ind = 0;

		if (pageParts.length > ind && pageParts[ind].isBlank())
			ind++;
		if (pageParts.length > ind && !pageParts[ind].isBlank()) {

			appCode = pageParts[ind];
			ind++;
			if (pageParts.length > ind && !pageParts[ind].isBlank() && "SYSTEM".equals(clientCode)) {
				clientCode = pageParts[ind];
			}

			urlClientCode.put(beforeBreakPath, Tuples.of(clientCode, appCode));
		}

		return modifyRequest(exchange, chain, modifiedRequestPath, clientCode, appCode);
	}

	private Mono<Void> modifyRequest(ServerWebExchange exchange, GatewayFilterChain chain, String modifiedRequestPath,
	        String clientCode, String appCode) {

		Builder req = exchange.getRequest()
		        .mutate();

		logger.debug("{} : clientCode - {}, appCode - {}", exchange.getRequest()
		        .getPath(), clientCode, appCode);

		req.header("clientCode", clientCode);
		req.header("appCode", appCode);
		ServerHttpRequest modifiedRequest = req.path(modifiedRequestPath)
		        .build();

		return chain.filter(exchange.mutate()
		        .request(modifiedRequest)
		        .build());
	}

	private Tuple3<String, String, String> getSchemeHostPort(ServerWebExchange exchange) {

		URI uri = exchange.getRequest()
		        .getURI();

		HttpHeaders header = exchange.getRequest()
		        .getHeaders();
		String uriScheme = header.getFirst("X-Forwarded-Proto");
		String uriHost = header.getFirst("X-Forwarded-Host");
		String uriPort = header.getFirst("X-Forwarded-Port");

		if (uriScheme == null)
			uriScheme = uri.getScheme();
		if (uriHost == null)
			uriHost = uri.getHost();
		if (uriPort == null)
			uriPort = "" + uri.getPort();

		int ind = uriHost.indexOf(':');
		if (ind != -1)
			uriHost = uriHost.substring(0, ind);

		return Tuples.of(uriScheme, uriHost, uriPort);
	}

	private Mono<Tuple2<String, String>> getClientCode(String uriScheme, String uriHost, String uriPort) {

		Mono<String> uriKey = cacheService.makeKey(uriScheme, uriHost, ":", uriPort);

		return FlatMapUtil.flatMapMono(

		        () -> uriKey,

		        key -> cacheService.<Tuple2<String, String>>get(CACHE_NAME_GATEWAY_URL_CLIENT_APP_CODE, key)
		                .switchIfEmpty(Mono.defer(() -> this.security.getClientCode(uriScheme, uriHost, uriPort)
		                        .defaultIfEmpty(Tuples.of("SYSTEM", "nothing"))
		                        .flatMap(cod -> cacheService.put(CACHE_NAME_GATEWAY_URL_CLIENT_APP_CODE, cod, key)))));
	}
}
