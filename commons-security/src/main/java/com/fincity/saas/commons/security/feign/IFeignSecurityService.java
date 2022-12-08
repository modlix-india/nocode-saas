package com.fincity.saas.commons.security.feign;

import java.math.BigInteger;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import com.fincity.saas.common.security.jwt.ContextAuthentication;

import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;

@ReactiveFeignClient(name = "security")
public interface IFeignSecurityService {

	@GetMapping("${security.feign.contextAuthentication:/api/security/internal/securityContextAuthentication}")
	public Mono<ContextAuthentication> contextAuthentication(@RequestHeader("Authorization") String authorization,
	        @RequestHeader("X-Forwarded-Host") String forwardedHost,
	        @RequestHeader("X-Forwarded-Port") String forwardedPort);

	@GetMapping("${security.feign.isBeingManaged:/api/security/clients/internal/isBeingManaged}")
	public Mono<Boolean> isBeingManaged(@RequestParam String managingClientCode, @RequestParam String clientCode);
	
	@GetMapping("${security.feign.isUserBeingManaged:/api/security/clients/internal/isUserBeingManaged}")
	public Mono<Boolean> isUserBeingManaged(@RequestParam BigInteger userId, @RequestParam String clientCode);

	@GetMapping("${security.feign.hasReadAccess:/api/security/apps/internal/hasReadAccess}")
	public Mono<Boolean> hasReadAccess(@RequestParam String appCode,
	        @RequestParam String clientCode);
	
	@GetMapping("${security.feign.hasWriteAccess:/api/security/apps/internal/hasWriteAccess}")
	public Mono<Boolean> hasWriteAccess(@RequestParam String appCode,
	        @RequestParam String clientCode);
	
	@GetMapping("${security.feign.hasWriteAccess:/api/security/apps/internal/appInheritance}")
	public Mono<List<String>> appInheritance(@RequestParam String appCode,
	        @RequestParam String clientCode);
}
