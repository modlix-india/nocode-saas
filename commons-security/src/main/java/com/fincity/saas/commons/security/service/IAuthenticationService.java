package com.fincity.saas.commons.security.service;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;

import reactor.core.publisher.Mono;

public interface IAuthenticationService {

    String CACHE_NAME_TOKEN = "tokenCache";

    Mono<Authentication> getAuthentication(
		    boolean isBasic, String bearerToken, String clientCode, String appCode, ServerHttpRequest request);
}
