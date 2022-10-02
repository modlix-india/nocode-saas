package com.fincity.saas.commons.security.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.security.feign.IFeignSecurityService;

import reactor.core.publisher.Mono;

@Service
public class FeignAuthenticationService implements IAuthenticationService {

	@Autowired(required = false)
	private IFeignSecurityService feignAuthService;

	@Override
	public Mono<Authentication> getAuthentication(boolean isBasic, String bearerToken, ServerHttpRequest request) {

		if (feignAuthService == null)
			return Mono.empty();

		String host = request.getURI()
		        .getHost();
		String port = "" + request.getURI()
		        .getPort();

		List<String> forwardedHost = request.getHeaders()
		        .get("X-Forwarded-Host");

		if (forwardedHost != null && !forwardedHost.isEmpty()) {
			host = forwardedHost.get(0);
		}

		List<String> forwardedPort = request.getHeaders()
		        .get("X-Forwarded-Port");
		if (forwardedPort != null && !forwardedPort.isEmpty()) {
			port = forwardedPort.get(0);
		}

		
		return this.feignAuthService.contextAuthentication(isBasic ? "basic " + bearerToken : bearerToken, host, port)
		        .map(Authentication.class::cast)

		;

	}

}