package com.fincity.gateway;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuple4;

@ReactiveFeignClient(name = "security")
public interface IFeignSecurityClient {

	@GetMapping("${security.feign.getClientCode:/api/security/clients/internal/getClientNAppCode}")
	public Mono<Tuple2<String, String>> getClientCode(@RequestParam String scheme, @RequestParam String host,
	        @RequestParam String port);

	/**
	 * Resolves a hostname to (clientCode, appCode, urlType). urlType is "DRAFT"
	 * when the hostname serves an app's draft surface, and that is the only thing
	 * that causes the gateway to mark a request as such.
	 */
	@GetMapping("${security.feign.getClientCodeNType:/api/security/clients/internal/getClientNAppCodeNType}")
	public Mono<Tuple3<String, String, String>> getClientCodeNType(@RequestParam String scheme,
	        @RequestParam String host, @RequestParam String port);

	/**
	 * Whether a `t-<32 hex>` hostname's draft-edit token grants the draft surface,
	 * and for which app and client:
	 * {@code (allowed, expiresAtEpochSeconds, appCode, clientCode)}.
	 *
	 * The expiry comes back rather than a bare verdict because the gateway caches
	 * this and CacheService has no per-entry TTL -- its Caffeine backstop outlives a
	 * token, so the gateway re-checks the timestamp itself and a stale entry cannot
	 * authorise anything. It is a String and not a number because the tuple
	 * deserializer reads elements as plain Objects: epoch seconds fit in an Integer,
	 * and a declared Long would blow up on the cast.
	 *
	 * Blank codes mean the request had no /appCode/clientCode path prefix to offer,
	 * and the token's own codes come back for the gateway to adopt.
	 *
	 * Its own property key, deliberately: sharing one with a neighbouring method has
	 * already made several endpoints unreachable in this codebase.
	 */
	@GetMapping("${security.feign.resolveDraftToken:/api/security/clienturls/internal/draft/token/resolve}")
	public Mono<Tuple4<Boolean, String, String, String>> resolveDraftToken(@RequestParam String host,
	        @RequestParam String appCode, @RequestParam String clientCode);
}
