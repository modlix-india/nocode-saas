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

	/**
	 * Deliberately NOT "gatewayClientAppCode", which is what this cache was called
	 * while it held a Tuple2 of (clientCode, appCode).
	 *
	 * The value gained a third element when hostname resolution started reporting
	 * the surface. Keeping the old name meant a newly deployed gateway read an
	 * entry an older one had written and cast Tuple2 to Tuple3, which threw on
	 * EVERY request until someone evicted the cache by hand. Leaving the old
	 * endpoint in place makes the rolling deploy safe on the wire; it is the
	 * shared cache key that makes it unsafe, so the key has to move too.
	 */
	private static final String CACHE_NAME_GATEWAY_URL_CLIENT_APP_CODE = "gatewayClientAppCodeType";
	private static final String CAHCE_NAME_URLPART = "clienturlpart";

	private static final String DEFAULT_CLIENT = "SYSTEM";
	private static final String DEFAULT_APP = "nothing";

	private static final String LIVE = "LIVE";
	private static final String DRAFT = "DRAFT";

	/** Mirrors LogUtil.DRAFT_KEY, which the gateway does not depend on. */
	private static final String DRAFT_HEADER = "x-draft";

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
			// The strip below in modifyRequest is the only place x-draft is removed,
			// and this branch returns before reaching it. No route with this id
			// exists in either gateway.yml today, so nothing takes this path, but
			// that makes header forgery one config line away rather than impossible.
			return chain.filter(exchange.mutate()
					.request(exchange.getRequest()
							.mutate()
							.headers(h -> h.remove(DRAFT_HEADER))
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
			if (pageIndex == -1 || pageIndex > index)
				codesPart = "";
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

		modifiedPath = modifiedPath.trim();
		int length = modifiedPath.length();

		final String finModifiedPath = length != 1 && modifiedPath.charAt(length - 1) == '/'
				? modifiedPath.substring(0, modifiedPath.length() - 1)
				: modifiedPath;
		// A path-prefixed /clientCode/appCode/ URL is never a draft host, so that
		// branch resolves to LIVE. Only hostname resolution can yield DRAFT.
		return this.getCodesFromURL(codesPart)
				.map(tup -> Tuples.of(tup.getT1(), tup.getT2(), LIVE))
				.switchIfEmpty(Mono.defer(() -> this.getClientCodeNType(this.getSchemeHostPort(exchange))))
				.flatMap(tup -> this.modifyRequest(exchange, chain, finModifiedPath, tup.getT1(), tup.getT2(),
						DRAFT.equalsIgnoreCase(tup.getT3())));
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
			String clientCode, String appCode, boolean draft) {

		Builder req = exchange.getRequest()
				.mutate();

		logger.debug("{} : clientCode - {}, appCode - {}, draft - {}", exchange.getRequest()
				.getPath(), clientCode, appCode, draft);

		HttpHeaders inHeaders = exchange.getRequest()
				.getHeaders();

		String suppliedAppCode = inHeaders.getFirst("appCode");
		String suppliedClientCode = inHeaders.getFirst("clientCode");

		if (StringUtil.safeIsBlank(suppliedAppCode)) {
			req.header("appCode", appCode);
		}
		if (StringUtil.safeIsBlank(suppliedClientCode)) {
			req.header("clientCode", clientCode);
		}

		// The draft marker is stripped from EVERY request and then set only from
		// the resolved hostname, so it can only ever originate here.
		//
		// Note this deliberately does NOT follow the appCode/clientCode handling
		// above, which only fills in a value when the caller did not supply one and
		// is therefore caller-overridable by design. If x-draft worked that way,
		// any visitor could read unpublished content on the live host by setting a
		// header, which defeats the entire access model.
		//
		// Filling those two in is not enough on its own, though. The hostname
		// decides WHICH SURFACE, the headers decide WHOSE app, and until they were
		// compared the two were independent: a draft host for one app, plus an
		// appCode header naming another, served the second app's unpublished work to
		// anyone holding the first app's link. Reproduced anonymously against the
		// local stack before this check existed.
		//
		// So a draft surface is only granted when the request is actually FOR the
		// app and client that hostname resolves to. A mismatch downgrades to live
		// rather than being refused: the request is still a legitimate read of a
		// published page, and refusing it would break the path-prefixed
		// /clientCode/appCode/page/ form and every tool that pairs a forwarded host
		// with explicit codes.
		req.headers(h -> h.remove(DRAFT_HEADER));
		if (draft && codesMatchResolved(suppliedAppCode, appCode, suppliedClientCode, clientCode))
			req.header(DRAFT_HEADER, "true");

		ServerHttpRequest modifiedRequest = req.path(modifiedRequestPath)
				.build();

		return chain.filter(exchange.mutate()
				.request(modifiedRequest)
				.build());
	}

	/**
	 * Whether the caller-supplied codes, if any, are the ones this hostname
	 * resolves to.
	 *
	 * A blank supplied code is not a mismatch: it means the caller named nothing
	 * and the resolved value was filled in above, which is the ordinary case.
	 * Compared case-insensitively, since clientCode is conventionally upper case
	 * and appCode lower, and a case difference is not an attempt at anything.
	 */
	private static boolean codesMatchResolved(String suppliedAppCode, String resolvedAppCode,
			String suppliedClientCode, String resolvedClientCode) {

		return matches(suppliedAppCode, resolvedAppCode) && matches(suppliedClientCode, resolvedClientCode);
	}

	private static boolean matches(String supplied, String resolved) {
		return StringUtil.safeIsBlank(supplied) || supplied.equalsIgnoreCase(resolved);
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

	private Mono<Tuple3<String, String, String>> getClientCodeNType(Tuple3<String, String, String> tup) {

		String uriScheme = tup.getT1();
		String uriHost = tup.getT2();
		String uriPort = tup.getT3();

		return cacheService.cacheValueOrGet(CACHE_NAME_GATEWAY_URL_CLIENT_APP_CODE,

				() -> this.security.getClientCodeNType(uriScheme, uriHost, uriPort)
						.defaultIfEmpty(Tuples.of(DEFAULT_CLIENT, DEFAULT_APP, LIVE)),

				uriScheme, ":", uriHost, ":", uriPort);
	}
}
