package com.fincity.saas.commons.security.feign;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import com.fincity.saas.common.security.jwt.ContextAuthentication;

import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;

@ReactiveFeignClient("${security.feign.url:http://security/api/security/}")
public interface IFeignGetTokenService {

	@GetMapping("${security.feign.contextAuthentication:internal/securityContextAuthentication}")
	public Mono<ResponseEntity<ContextAuthentication>> contextAuthentication(
	        @RequestHeader("Authorization") String authorization);
}
