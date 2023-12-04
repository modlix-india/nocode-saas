package com.fincity.saas.multi.fiegn;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;

@ReactiveFeignClient(name = "ui")
public interface IFeignUIService {

	@PostMapping("${ui.transport:/api/ui/transports/makeTransport}")
	public Mono<Map<String, Object>> makeTransport(
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@RequestHeader("X-Forwarded-Host") String forwardedHost,
			@RequestHeader("X-Forwarded-Port") String forwardedPort,
			@RequestHeader("clientCode") String clientCode,
			@RequestHeader("appCode") String headerAppCode,
			@RequestBody Map<String, Object> request);

	@PostMapping("${core.transport.createAndApply:/api/ui/transports/createAndApply}")
	public Mono<Boolean> createAndApplyTransport(
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@RequestHeader("X-Forwarded-Host") String forwardedHost,
			@RequestHeader("X-Forwarded-Port") String forwardedPort,
			@RequestHeader("clientCode") String clientCode,
			@RequestHeader("appCode") String headerAppCode,
			@RequestParam Boolean isForBaseApp,
			@RequestParam String applicationCode,
			@RequestBody Object uiDefinition);
}
