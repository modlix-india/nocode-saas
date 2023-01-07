package com.fincity.saas.commons.security.service;

import java.math.BigInteger;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.service.CacheService;

import reactor.core.publisher.Mono;

@Service
public class FeignAuthenticationService implements IAuthenticationService {

	private static final String CACHE_NAME_BEING_MANAGED = "beingManaged";
	private static final String CACHE_NAME_USER_BEING_MANAGED = "userBeingManaged";
	private static final String CACHE_NAME_APP_READ_ACCESS = "appReadAccess";
	private static final String CACHE_NAME_APP_WRITE_ACCESS = "appWriteAccess";

	@Autowired(required = false)
	private IFeignSecurityService feignAuthService;

	@Autowired
	private CacheService cacheService;

	@Override
	public Mono<Authentication> getAuthentication(boolean isBasic, String bearerToken, ServerHttpRequest request) {

		if (feignAuthService == null)
			return Mono.empty();
		
		if (request.getURI().getPath().indexOf("actuator/") != -1)
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
			int ind = port.indexOf(',');
			if (ind != -1) {
				port = port.substring(0, ind);
			}
		}

		return this.feignAuthService.contextAuthentication(isBasic ? "basic " + bearerToken : bearerToken, host, port)
		        .map(Authentication.class::cast);
	}

	public Mono<Boolean> isBeingManaged(String managingClientCode, String clientCode) {

		return cacheService.cacheEmptyValueOrGet(CACHE_NAME_BEING_MANAGED,
		        () -> this.feignAuthService.isBeingManaged(managingClientCode, clientCode), managingClientCode, ":",
		        clientCode);
	}

	public Mono<Boolean> isUserBeingManaged(Object userId, String clientCode) {

		return FlatMapUtil.flatMapMonoWithNull(

		        () -> cacheService.makeKey(clientCode, ":", userId),

		        key -> cacheService.<Boolean>get(CACHE_NAME_USER_BEING_MANAGED, key),

		        (key, value) ->
				{

			        if (value != null)
				        return Mono.just(value);

			        BigInteger biUserId = userId instanceof BigInteger id ? id : new BigInteger(userId.toString());

			        return this.feignAuthService.isUserBeingManaged(biUserId, clientCode)
			                .flatMap(v -> cacheService.put(CACHE_NAME_USER_BEING_MANAGED, v, key));
		        });
	}

	public Mono<Boolean> hasReadAccess(String appCode, String clientCode) {

		return FlatMapUtil.flatMapMonoWithNull(

		        () -> cacheService.makeKey(appCode, ":", appCode),

		        key -> cacheService.<Boolean>get(CACHE_NAME_APP_READ_ACCESS, key),

		        (key, value) ->
				{

			        if (value != null)
				        return Mono.just(value);

			        return this.feignAuthService.hasReadAccess(appCode, clientCode)
			                .flatMap(v -> cacheService.put(CACHE_NAME_APP_READ_ACCESS, v, key));
		        });
	}

	public Mono<Boolean> hasWriteAccess(String appCode, String clientCode) {

		return FlatMapUtil.flatMapMonoWithNull(

		        () -> cacheService.makeKey(appCode, ":", appCode),

		        key -> cacheService.<Boolean>get(CACHE_NAME_APP_WRITE_ACCESS, key),

		        (key, value) ->
				{

			        if (value != null)
				        return Mono.just(value);

			        return this.feignAuthService.hasWriteAccess(appCode, clientCode)
			                .flatMap(v -> cacheService.put(CACHE_NAME_APP_WRITE_ACCESS, v, key));
		        });
	}
}