package com.fincity.gateway;

import java.net.URI;
import java.time.Instant;
import java.util.regex.Pattern;

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
import reactor.util.function.Tuple4;
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

	/**
	 * Written and read only here, and deliberately never evicted from outside.
	 *
	 * Unlike {@code gatewayClientAppCodeType} above, security does not clear this
	 * one, because it could not: {@code CacheService} scopes a cache name by the
	 * service's own {@code redis.cache.prefix}, {@code gtw} here against {@code sec}
	 * in security, so an evictAll there would clear a cache nothing writes.
	 * Freshness comes from the trust window on each entry instead.
	 */
	private static final String CACHE_NAME_GATEWAY_DRAFT_TOKEN = "gatewayDraftToken";

	/**
	 * An editing session's draft-surface grant, carried as the first host label.
	 *
	 * Matched whole, so an ordinary app hostname beginning with "t" is not mistaken
	 * for one. Mirrors {@code ClientUrlService.DRAFT_TOKEN_LABEL}, which mints it.
	 */
	private static final Pattern DRAFT_TOKEN_LABEL = Pattern.compile("^t-[0-9a-f]{32}$");

	/**
	 * How long a resolved answer may be trusted without asking security again.
	 *
	 * The ceiling on three separate staleness windows: how long after a heartbeat
	 * before an extension is honoured, how long after a token is deleted before it
	 * stops being, and how long a denial is remembered for a bad hostname.
	 */
	private static final long DRAFT_TOKEN_TRUST_SECONDS = 60;

	/** No grant, no codes to adopt; the trust window is stamped on before caching. */
	private static final Tuple4<Boolean, String, String, String> DRAFT_TOKEN_DENIED = Tuples.of(Boolean.FALSE, "0", "",
			"");

	private static final String DEFAULT_CLIENT = "SYSTEM";
	private static final String DEFAULT_APP = "nothing";

	private static final String LIVE = "LIVE";
	private static final String DRAFT = "DRAFT";

	/** Mirrors LogUtil.DRAFT_KEY, which the gateway does not depend on. */
	private static final String DRAFT_HEADER = "x-draft";

	private static final String APP_CODE_HEADER = "appCode";
	private static final String CLIENT_CODE_HEADER = "clientCode";

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
		return this.resolveCodesAndSurface(exchange, codesPart)
				.flatMap(tup -> this.modifyRequest(exchange, chain, finModifiedPath, tup.getT1(), tup.getT2(),
						DRAFT.equalsIgnoreCase(tup.getT3())));
	}

	/**
	 * (clientCode, appCode, surface) for this request.
	 *
	 * Two hostnames can yield DRAFT and they are not the same thing. The permanent
	 * draft link is a CLIENT_URL row and resolves through getClientCodeNType, which
	 * is why a path-prefixed URL on it still reads LIVE: the path names an app and
	 * client the link was never issued for.
	 *
	 * A `t-<32 hex>` hostname is an editing session's grant, and there the path
	 * prefix is the whole point -- it names the client being previewed, and the
	 * token names who is entitled to preview it. So this branch deliberately does
	 * consult the host alongside the path, which the other one must not.
	 *
	 * Everything else takes exactly the path it took before: the label test is a
	 * regex over the first host label and no ordinary request pays for a lookup.
	 */
	private Mono<Tuple3<String, String, String>> resolveCodesAndSurface(ServerWebExchange exchange, String codesPart) {

		Tuple3<String, String, String> schemeHostPort = this.getSchemeHostPort(exchange);
		final String host = schemeHostPort.getT2();

		if (!DRAFT_TOKEN_LABEL.matcher(firstLabel(host)).matches())
			// A path-prefixed /clientCode/appCode/ URL is never a draft host, so that
			// branch resolves to LIVE. Only hostname resolution can yield DRAFT.
			return this.getCodesFromURL(codesPart)
					.map(tup -> Tuples.of(tup.getT1(), tup.getT2(), LIVE))
					.switchIfEmpty(Mono.defer(() -> this.getClientCodeNType(schemeHostPort)));

		// Codes come from the path when it has them, then from the headers, then from
		// the token itself. The header step is not optional: SSR renders the shell by
		// calling back through the gateway with explicit appCode/clientCode headers
		// and no path prefix, so without it those calls would be checked against the
		// token's own client, disagree with what SSR asked for, and the pre-render
		// would silently come back live.
		HttpHeaders inHeaders = exchange.getRequest()
				.getHeaders();

		return this.getCodesFromURL(codesPart)
				.flatMap(tup -> this.draftTokenSurface(host, tup.getT1(), tup.getT2()))
				.switchIfEmpty(Mono.defer(() -> this.draftTokenSurface(host,
						StringUtil.safeValueOf(inHeaders.getFirst(CLIENT_CODE_HEADER), ""),
						StringUtil.safeValueOf(inHeaders.getFirst(APP_CODE_HEADER), ""))));
	}

	/**
	 * Resolve a draft-edit token host, and fall back to LIVE rather than refusing.
	 *
	 * A token that is unknown, expired or issued for another app is not an error:
	 * the request is still a legitimate read of a published page, exactly as a
	 * mismatched draft host is. It also means a canvas whose editor has gone away
	 * degrades to showing the live app instead of breaking.
	 *
	 * Caching is self-correcting rather than evicted, because it cannot be evicted
	 * from outside. `CacheService.evictAll` scopes the cache name by the service's
	 * own `redis.cache.prefix` -- `gtw` here, `sec` in security -- so security
	 * clearing "gatewayDraftToken" clears its own copy and never touches this one.
	 * Instead every cached answer carries the second until which it may be trusted,
	 * and the entry is simply ignored once that passes.
	 *
	 * That window is the smaller of the token's own expiry and a minute. The expiry
	 * bound stops a grant outliving its token, which it otherwise would: CacheService
	 * has no per-entry TTL and its Caffeine backstop is an hour, twice a token's
	 * life. The minute bound is what makes the other two directions work -- a
	 * heartbeat's extension is picked up, and a deleted token stops being honoured,
	 * within sixty seconds rather than at the end of the original window.
	 */
	private Mono<Tuple3<String, String, String>> draftTokenSurface(String host, String clientCode, String appCode) {

		return cacheService
				.<Tuple4<Boolean, String, String, String>>get(CACHE_NAME_GATEWAY_DRAFT_TOKEN, host, ":", appCode, ":",
						clientCode)
				.filter(res -> stillTrusted(res.getT2()))
				.switchIfEmpty(Mono.defer(() -> this.security.resolveDraftToken(host, appCode, clientCode)
						.defaultIfEmpty(DRAFT_TOKEN_DENIED)
						.map(GatewayFilter::withTrustWindow)
						.flatMap(res -> cacheService.put(CACHE_NAME_GATEWAY_DRAFT_TOKEN, res, host, ":", appCode, ":",
								clientCode))))
				.map(res -> {

					String effAppCode = StringUtil.safeIsBlank(appCode) ? res.getT3() : appCode;
					String effClientCode = StringUtil.safeIsBlank(clientCode) ? res.getT4() : clientCode;

					if (StringUtil.safeIsBlank(effAppCode))
						effAppCode = DEFAULT_APP;
					if (StringUtil.safeIsBlank(effClientCode))
						effClientCode = DEFAULT_CLIENT;

					return Tuples.of(effClientCode, effAppCode, Boolean.TRUE.equals(res.getT1()) ? DRAFT : LIVE);
				})
				.onErrorReturn(Tuples.of(StringUtil.safeIsBlank(clientCode) ? DEFAULT_CLIENT : clientCode,
						StringUtil.safeIsBlank(appCode) ? DEFAULT_APP : appCode, LIVE));
	}

	/**
	 * Replace security's answer's expiry with how long this gateway may trust it.
	 *
	 * A denial gets the flat minute: without it nothing would ever be cached for a
	 * bad hostname and every request on one would reach security.
	 */
	private static Tuple4<Boolean, String, String, String> withTrustWindow(Tuple4<Boolean, String, String, String> res) {

		long horizon = Instant.now()
				.getEpochSecond() + DRAFT_TOKEN_TRUST_SECONDS;

		if (!Boolean.TRUE.equals(res.getT1()))
			return Tuples.of(Boolean.FALSE, String.valueOf(horizon), res.getT3(), res.getT4());

		return Tuples.of(Boolean.TRUE, String.valueOf(Math.min(epochSeconds(res.getT2()), horizon)), res.getT3(),
				res.getT4());
	}

	private static boolean stillTrusted(String untilEpochSeconds) {
		return epochSeconds(untilEpochSeconds) > Instant.now()
				.getEpochSecond();
	}

	/** Epoch seconds as text, because the tuple deserializer would hand back an Integer. */
	private static long epochSeconds(String value) {

		try {
			return Long.parseLong(value);
		} catch (NumberFormatException e) {
			return 0L;
		}
	}

	private static String firstLabel(String host) {

		if (StringUtil.safeIsBlank(host))
			return "";

		int dot = host.indexOf('.');
		return dot == -1 ? host : host.substring(0, dot);
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

		String suppliedAppCode = inHeaders.getFirst(APP_CODE_HEADER);
		String suppliedClientCode = inHeaders.getFirst(CLIENT_CODE_HEADER);

		if (StringUtil.safeIsBlank(suppliedAppCode)) {
			req.header(APP_CODE_HEADER, appCode);
		}
		if (StringUtil.safeIsBlank(suppliedClientCode)) {
			req.header(CLIENT_CODE_HEADER, clientCode);
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
