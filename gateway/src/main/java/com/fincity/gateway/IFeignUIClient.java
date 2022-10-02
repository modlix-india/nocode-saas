package com.fincity.gateway;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

@ReactiveFeignClient(name = "ui")
public interface IFeignUIClient {

	@GetMapping("${ui.feign.getAppCode:/api/ui/applications/internal/getAppNClientCode}")
	public Mono<Tuple2<String, String>> getAppNClientCode(@RequestParam String scheme, @RequestParam String host,
	        @RequestParam String port);
}
