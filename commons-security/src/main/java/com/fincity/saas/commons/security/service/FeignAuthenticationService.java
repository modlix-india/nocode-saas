package com.fincity.saas.commons.security.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.security.feign.IFeignGetTokenService;

import reactor.core.publisher.Mono;

@Service
public class FeignAuthenticationService implements IAuthenticationService {

	@Autowired(required = false)
	private IFeignGetTokenService feignAuthService;

	@Override
	public Mono<Authentication> getAuthentication(boolean isBasic, String bearerToken, ServerHttpRequest request) {

		if (feignAuthService == null)
			return Mono.empty();

		return this.feignAuthService.contextAuthentication(isBasic ? "basic " + bearerToken : bearerToken)
		        .map(Authentication.class::cast);
	}

}
