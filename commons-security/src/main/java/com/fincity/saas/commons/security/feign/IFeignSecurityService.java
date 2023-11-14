package com.fincity.saas.commons.security.feign;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import com.fincity.saas.commons.security.dto.App;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;

import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;

@ReactiveFeignClient(name = "security")
public interface IFeignSecurityService {

	@GetMapping("${security.feign.contextAuthentication:/api/security/internal/securityContextAuthentication}")
	public Mono<ContextAuthentication> contextAuthentication(
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@RequestHeader("X-Forwarded-Host") String forwardedHost,
			@RequestHeader("X-Forwarded-Port") String forwardedPort,
			@RequestHeader("clientCode") String clientCode,
			@RequestHeader("appCode") String appCode);

	@GetMapping("${security.feign.isBeingManaged:/api/security/clients/internal/isBeingManaged}")
	public Mono<Boolean> isBeingManaged(@RequestParam String managingClientCode, @RequestParam String clientCode);

	@GetMapping("${security.feign.isUserBeingManaged:/api/security/clients/internal/isUserBeingManaged}")
	public Mono<Boolean> isUserBeingManaged(@RequestParam BigInteger userId, @RequestParam String clientCode);

	@GetMapping("${security.feign.hasReadAccess:/api/security/applications/internal/hasReadAccess}")
	public Mono<Boolean> hasReadAccess(@RequestParam String appCode, @RequestParam String clientCode);

	@GetMapping("${security.feign.hasWriteAccess:/api/security/applications/internal/hasWriteAccess}")
	public Mono<Boolean> hasWriteAccess(@RequestParam String appCode, @RequestParam String clientCode);

	@GetMapping("${security.feign.validClientCode:/api/security/clients/internal/validateClientCode}")
	public Mono<Boolean> validClientCode(@RequestParam String clientCode);

	@GetMapping("${security.feign.hasWriteAccess:/api/security/applications/internal/appInheritance}")
	public Mono<List<String>> appInheritance(@RequestParam String appCode, @RequestParam String urlClientCode,
			@RequestParam String clientCode);

	@GetMapping("${security.feign.token:/api/security/ssl/token/{token}}")
	public Mono<String> token(@PathVariable("token") String token);

	@GetMapping("${security.feign.getByAppCode:/api/security/applications/internal/appCode/{appCode}}")
	public Mono<App> getAppCode(@PathVariable("appCode") String appCode);

	@DeleteMapping("${security.feign.deleteByAppId:/api/security/applications/{id}}")
	public Mono<Boolean> deleteByAppId(@RequestHeader(name = "Authorization", required = false) String authorization,
			@PathVariable("id") BigInteger id);

	@GetMapping("${security.feign.transport:/api/security/transports/makeTransport}")
	public Mono<Map<String, Object>> makeTransport(
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@RequestHeader("X-Forwarded-Host") String forwardedHost,
			@RequestHeader("X-Forwarded-Port") String forwardedPort,
			@RequestHeader("clientCode") String clientCode,
			@RequestHeader("appCode") String headerAppCode,
			@RequestParam("applicationCode") String applicationCode);
}
