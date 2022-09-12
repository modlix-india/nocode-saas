package com.fincity.saas.commons.security.service;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

@Service
public class FeignAuthenticationService implements IAuthenticationService {

	@Override
	public Mono<Authentication> getAuthentication(boolean isBasic, String bearerToken, ServerHttpRequest request) {
		// TODO Auto-generated method stub
		return null;
	}

}
