package com.fincity.saas.commons.security.service;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;

import reactor.core.publisher.Mono;

public interface IAuthenticationService {
	
	public static final String CACHE_NAME_TOKEN = "tokenCache";

	public Mono<Authentication> getAuthentication(boolean isBasic, String bearerToken, String clientCode, String appCode, ServerHttpRequest request);
}
