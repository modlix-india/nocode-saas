package com.fincity.saas.commons.security.service;

import java.math.BigInteger;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.security.dto.App;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.LogUtil;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class FeignAuthenticationService implements IAuthenticationService {

	private static final String CACHE_NAME_BEING_MANAGED = "beingManaged";
	private static final String CACHE_NAME_USER_BEING_MANAGED = "userBeingManaged";
	private static final String CACHE_NAME_APP_READ_ACCESS = "appReadAccess";
	private static final String CACHE_NAME_APP_WRITE_ACCESS = "appWriteAccess";
	private static final String CACHE_NAME_APP_BY_APPCODE_EXPLICIT = "byAppCodeExplicit";
	private static final String CACHE_NAME_APP_DEP_LIST = "appDepList";

	@Autowired(required = false)
	private IFeignSecurityService feignAuthService;

	@Autowired
	private CacheService cacheService;

	@Override
	public Mono<Authentication> getAuthentication(boolean isBasic, String bearerToken, String clientCode,
			String appCode, ServerHttpRequest request) {

		return FlatMapUtil.flatMapMonoWithNull(

				() -> cacheService.<Authentication>get(CACHE_NAME_TOKEN, bearerToken),

				cToken -> {

					if (cToken != null)
						return Mono.just(cToken);

					return this.getAuthenticationFromSecurity(isBasic, bearerToken, clientCode, appCode, request)
							.flatMap(e -> {

								if (!e.isAuthenticated())
									return Mono.just(e);

								return cacheService.put(CACHE_NAME_TOKEN, e, bearerToken);
							});
				}

		)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "FeignAuthenticationService.getAuthentication"));
	}

	private Mono<Authentication> getAuthenticationFromSecurity(boolean isBasic, String bearerToken, String clientCode,
			String appCode, ServerHttpRequest request) {

		if (feignAuthService == null)
			return Mono.empty();

		if (request.getURI()
				.getPath()
				.indexOf("actuator/") != -1)
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

		return this.feignAuthService
				.contextAuthentication(isBasic ? "basic " + bearerToken : bearerToken, host, port, clientCode, appCode)
				.map(Authentication.class::cast);
	}

	public Mono<Boolean> isBeingManaged(String managingClientCode, String clientCode) {

		return cacheService.cacheEmptyValueOrGet(CACHE_NAME_BEING_MANAGED,
				() -> this.feignAuthService.isBeingManaged(managingClientCode, clientCode), managingClientCode, ":",
				clientCode);
	}

	public Mono<Boolean> isUserBeingManaged(Object userId, String clientCode) {

		return cacheService.cacheValueOrGet(CACHE_NAME_USER_BEING_MANAGED, () -> {

			BigInteger biUserId = userId instanceof BigInteger id ? id : new BigInteger(userId.toString());

			return this.feignAuthService.isUserBeingManaged(biUserId, clientCode);
		}, clientCode, ":", userId);
	}

	public Mono<Boolean> hasReadAccess(String appCode, String clientCode) {

		return cacheService.cacheValueOrGet(CACHE_NAME_APP_READ_ACCESS,
				() -> this.feignAuthService.hasReadAccess(appCode, clientCode), appCode, ":", clientCode);
	}

	public Mono<Boolean> hasWriteAccess(String appCode, String clientCode) {

		return cacheService.cacheValueOrGet(CACHE_NAME_APP_WRITE_ACCESS,
				() -> this.feignAuthService.hasWriteAccess(appCode, clientCode), appCode, ":", clientCode);
	}

	public Mono<Boolean> isValidClientCode(String clientCode) {

		return this.feignAuthService.validClientCode(clientCode);
	}

	public Mono<App> getAppExplicitInfoByCode(String appCode) {
		return cacheService.cacheValueOrGet(CACHE_NAME_APP_BY_APPCODE_EXPLICIT,
				() -> this.feignAuthService.getAppExplicitInfoByCode(appCode), appCode);
	}

	public Mono<List<String>> getDependencies(String appCode) {
		return cacheService.cacheValueOrGet(CACHE_NAME_APP_DEP_LIST,
				() -> this.feignAuthService.getDependencies(appCode),
				appCode);
	}
}