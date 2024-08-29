package com.fincity.saas.ui.feign;

import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;

@ReactiveFeignClient(name = "core")
public interface IFeignCoreService {

	String PATH = "api/core/function/execute/{namespace}/{name}";
	String PATH_VARIABLE_NAMESPACE = "namespace";
	String PATH_VARIABLE_NAME = "name";

	@GetMapping(PATH)
	Mono<String> executeWith(@RequestHeader String appCode,
			@RequestHeader String clientCode,
			@PathVariable(PATH_VARIABLE_NAMESPACE) String namespace,
			@PathVariable(PATH_VARIABLE_NAME) String name,
			ServerHttpRequest request);

	@PostMapping(PATH)
	Mono<String> executeWith(@RequestHeader String appCode,
			@RequestHeader String clientCode,
			@PathVariable(PATH_VARIABLE_NAMESPACE) String namespace,
			@PathVariable(PATH_VARIABLE_NAME) String name,
			@RequestBody String jsonString);
}
