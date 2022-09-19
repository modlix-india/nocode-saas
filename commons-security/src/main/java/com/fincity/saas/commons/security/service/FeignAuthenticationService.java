package com.fincity.saas.commons.security.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.security.feign.IFeignGetTokenService;

import reactor.core.publisher.Mono;

@Service
public class FeignAuthenticationService implements IAuthenticationService {

	@Autowired
	private IFeignGetTokenService feignAuthService;

	@Override
	public Mono<Authentication> getAuthentication(boolean isBasic, String bearerToken, ServerHttpRequest request) {

		return this.feignAuthService.contextAuthentication(isBasic ? "basic " + bearerToken : bearerToken)
		        .map(ResponseEntity::getBody);
	}

}
