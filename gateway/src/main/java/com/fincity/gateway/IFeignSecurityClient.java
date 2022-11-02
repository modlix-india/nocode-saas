package com.fincity.gateway;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

@ReactiveFeignClient(name = "security")
public interface IFeignSecurityClient {

	@GetMapping("${security.feign.getClientCode:/api/security/clients/internal/getClientNAppCode}")
	public Mono<Tuple2<String, String>> getClientCode(@RequestParam String scheme, @RequestParam String host,
	        @RequestParam String port);
}
