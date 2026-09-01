package com.fincity.gateway;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;

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
}
