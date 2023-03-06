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

		String requestPath = exchange.getRequest()
		        .getPath()
		        .toString();

		int index = requestPath.indexOf("/api/");
		String codesPart = "";
		String modifiedPath = requestPath;

		if (index != -1) {

			codesPart = requestPath.substring(0, index);
			int pageIndex = requestPath.indexOf("/page/");
			if (pageIndex == -1 || pageIndex > index) codesPart = "";
			modifiedPath = requestPath.substring(index);
		} else {

			index = requestPath.indexOf("/manifest/");
			if (index != -1) {

				codesPart = requestPath.substring(0, index);
				modifiedPath = requestPath.substring(index);
			} else {

				index = requestPath.indexOf("/page/");

				if (index != -1) {
					codesPart = requestPath.substring(0, index);
					modifiedPath = requestPath.substring(index);
				}
			}
		}

		final String finModifiedPath = modifiedPath;
		return this.getCodesFromURL(codesPart)
		        .switchIfEmpty(Mono.defer(() -> this.getClientCode(this.getSchemeHostPort(exchange))))
		        .flatMap(tup -> this.modifyRequest(exchange, chain, finModifiedPath, tup.getT1(), tup.getT2()));
	}

	private Mono<Tuple2<String, String>> getCodesFromURL(String appClientCodePart) {

		if (StringUtil.safeIsBlank(appClientCodePart) || StringUtil.safeEquals(appClientCodePart, "/")) {

			return Mono.empty();
		}

		return cacheService.cacheValueOrGet(CAHCE_NAME_URLPART, () -> {

			String[] parts = appClientCodePart.split("/");
			if (parts.length > 2) {
				return Mono.just(Tuples.of(parts[2], parts[1]));
			}

			return Mono.empty();

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

	private Mono<Tuple2<String, String>> getClientCode(Tuple3<String, String, String> tup) {

		String uriScheme = tup.getT1();
		String uriHost = tup.getT2();
		String uriPort = tup.getT3();

		return cacheService.cacheValueOrGet(CACHE_NAME_GATEWAY_URL_CLIENT_APP_CODE,

		        () -> this.security.getClientCode(uriScheme, uriHost, uriPort)
		                .defaultIfEmpty(Tuples.of(DEFAULT_CLIENT, DEFAULT_APP)),

		        uriScheme, ":", uriHost, ":", uriPort);
	}
}
