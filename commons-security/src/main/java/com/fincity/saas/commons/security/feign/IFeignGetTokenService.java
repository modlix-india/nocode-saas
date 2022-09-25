package com.fincity.saas.commons.security.feign;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import com.fincity.saas.common.security.jwt.ContextAuthentication;

import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;

@ReactiveFeignClient(name = "security")
public interface IFeignGetTokenService {

	@GetMapping("${security.feign.contextAuthentication:/api/security/internal/securityContextAuthentication}")
	public Mono<ContextAuthentication> contextAuthentication(
	        @RequestHeader("Authorization") String authorization);
}
