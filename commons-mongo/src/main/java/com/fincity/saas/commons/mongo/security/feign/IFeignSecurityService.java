package com.fincity.saas.commons.mongo.security.feign;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;

@ReactiveFeignClient(name = "security")
public interface IFeignSecurityService {
	
	@GetMapping("${security.feign.hasWriteAccess:/api/security/apps/internal/appInheritance}")
	public Mono<List<String>> appInheritance(@RequestParam String appCode,
	        @RequestParam String clientCode);
}
