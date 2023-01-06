package com.fincity.gateway;

import java.net.URI;

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

import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.StringUtil;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

@Component
public class GatewayFilter implements GlobalFilter, Ordered {

	private static final String CACHE_NAME_GATEWAY_URL_CLIENT_APP_CODE = "gatewayClientAppCode";
	private static final String CAHCE_NAME_URLPART = "clienturlpart";

	private static final String DEFAULT_CLIENT = "SYSTEM";
	private static final String DEFAULT_APP = "nothing";

	@Autowired
	private CacheService cacheService;

	@Autowired
	private IFeignSecurityClient security;

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

		String appCode = finT.getT2();
		String clientCode = finT.getT1();

		String requestPath = exchange.getRequest()
		        .getPath()
		        .toString();

		int apiIndex = requestPath.indexOf("/api/");

		if (apiIndex == -1 && requestPath.endsWith("/manifest/manifest.json")) {

			return this
			        .getCodesFromURL(appCode, clientCode, requestPath.substring(0, requestPath.indexOf("/manifest/")))
			        .flatMap(codes -> this.modifyRequest(exchange, chain, "/manifest/manifest.json", codes.getT2(),
			                codes.getT1()));
		}

		String appClientCodePart = requestPath.substring(0,
		        apiIndex == -1 ? requestPath.indexOf("/page/") + 2 : apiIndex);

		String modifiedRequestPath = (apiIndex == -1) ? requestPath : requestPath.substring(apiIndex);
		if (DEFAULT_CLIENT.equals(clientCode)) {

			return this.getCodesFromURL(appCode, clientCode, appClientCodePart)
			        .flatMap(codes -> this.modifyRequest(exchange, chain, modifiedRequestPath, codes.getT2(),
			                codes.getT1()));
		}

		return this.modifyRequest(exchange, chain, modifiedRequestPath, clientCode, appCode);

	}

	private Mono<Tuple2<String, String>> getCodesFromURL(String appCode, String clientCode, String appClientCodePart) {

		if (StringUtil.safeIsBlank(appClientCodePart) || StringUtil.safeEquals(appClientCodePart, "/")) {

			return Mono.just(Tuples.of(appCode, clientCode));
		}

		return cacheService.cacheValueOrGet(CAHCE_NAME_URLPART, () -> {

			String[] parts = appClientCodePart.split("/");
			String aCode = appCode;
			String cCode = clientCode;
			if (parts.length > 1) {

				aCode = parts[1];
				if (parts.length > 2)
					cCode = parts[2];
			}

			return Mono.just(Tuples.of(aCode, cCode));

		}, appClientCodePart);
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

		return cacheService.cacheValueOrGet(CACHE_NAME_GATEWAY_URL_CLIENT_APP_CODE,

		        () -> this.security.getClientCode(uriScheme, uriHost, uriPort)
		                .defaultIfEmpty(Tuples.of(DEFAULT_CLIENT, DEFAULT_APP)),

		        uriScheme, uriHost, ":", uriPort)
		        .map(e ->
				{

			        logger.debug("{} {} {} -> {}", uriScheme, uriHost, uriPort, e);

			        return e;
		        });
	}
}
