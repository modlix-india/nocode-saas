package com.fincity.saas.ui.service;

import javax.inject.Inject;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.ui.document.URI;
import com.fincity.saas.ui.feign.IFeignCoreService;
import com.fincity.saas.ui.model.KIRunFxDefinition;
import com.fincity.saas.ui.repository.URIRepository;
import com.google.gson.JsonObject;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class URIService extends AbstractOverridableDataService<URI, URIRepository> {

	@Inject
	private IFeignCoreService iFeignCoreService;

	protected URIService() {
		super(URI.class);
	}

	public void setURIService(IFeignCoreService iFeignCoreService) {
		this.iFeignCoreService = iFeignCoreService;
	}

	@Override
	protected Mono<URI> updatableEntity(URI entity) {
		return FlatMapUtil.flatMapMono(

				() -> this.read(entity.getId()),

				existing -> {

					if (existing.getVersion() != entity.getVersion()) {
						return this.messageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
								AbstractMongoMessageResourceService.VERSION_MISMATCH);
					}

					existing.setUriString(entity.getUriString());
					existing.setScheme(entity.getScheme());
					existing.setFragment(entity.getFragment());
					existing.setAuthority(entity.getAuthority());
					existing.setUserInfo(entity.getUserInfo());
					existing.setHost(entity.getHost());
					existing.setPort(entity.getPort());
					existing.setPath(entity.getPath());
					existing.setQuery(entity.getQuery());
					existing.setQueryParams(entity.getQueryParams());
					existing.setUrlType(entity.getUrlType());
					existing.setWhitelist(entity.getWhitelist());
					existing.setBlacklist(entity.getBlacklist());
					existing.setAccessLimit(entity.getAccessLimit());
					existing.setAccessCount(entity.getAccessCount());
					existing.setKiRunFxDefinition(entity.getKiRunFxDefinition());
					existing.setRedirectionDefinitions(entity.getRedirectionDefinitions());
					existing.setPermission(entity.getPermission());

					existing.setVersion(existing.getVersion() + 1);

					return Mono.just(existing);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "URIService.updatableEntity"));
	}

	public Mono<ObjectWithUniqueID<String>> getResponse(ServerHttpRequest request, JsonObject jsonObject,
			String appCode, String clientCode) {

		String uriString = request.getURI().toString();

		return FlatMapUtil.flatMapMono(
				() -> this.readByUrlString(uriString, appCode, clientCode),
				uriObjectWithUniqueID -> {
					URI uri = uriObjectWithUniqueID.getObject();
					switch (uri.getUrlType()) {
						case KIRUN_FUNCTION -> {
							return executeKIRunFunction(request, jsonObject, uri.getKiRunFxDefinition(),
									uri.getAppCode(),
									uri.getClientCode());
						}
						case REDIRECTION -> {
							// TODO
						}
						case SHORTENED_URL -> {
							// TODO
						}
					}
					return Mono.empty();
				}).switchIfEmpty(Mono.empty());

	}

	private Mono<ObjectWithUniqueID<URI>> readByUrlString(String uriString, String appCode, String clientCode) {

		return FlatMapUtil.flatMapMono(

				() -> this.repo.findByUriStringAndAppCodeAndClientCode(uriString, appCode, clientCode),

				uri -> {

					if (StringUtil.safeIsBlank(uri.getPermission())) {
						return Mono.just(new ObjectWithUniqueID<>(uri))
								.contextWrite(Context.of(LogUtil.METHOD_NAME, "URIService.read [open Url Read]"));
					}

					return FlatMapUtil.flatMapMono(

							SecurityContextUtil::getUsersContextAuthentication,

							ca -> Mono.just(ca.isAuthenticated()),

							(ca, isAuthenticated) -> Boolean.TRUE.equals(isAuthenticated)
									? Mono.just(new ObjectWithUniqueID<>(uri))
									: Mono.empty())
							.contextWrite(Context.of(LogUtil.METHOD_NAME, "URIService.read [Authenticated Url read]"));
				}).switchIfEmpty(Mono.empty());
	}

	private Mono<ObjectWithUniqueID<String>> executeKIRunFunction(ServerHttpRequest request, JsonObject jsonObject,
			KIRunFxDefinition kiRunFxDefinition, String appCode, String clientCode) {

		return FlatMapUtil.flatMapMono(
				() -> {
					switch (request.getMethod()) {
						case GET, HEAD -> {
							return iFeignCoreService.executeWith(appCode, clientCode,
									kiRunFxDefinition.getNamespace(), kiRunFxDefinition.getName(), request);
						}
						case POST, PUT, PATCH, DELETE -> {
							return iFeignCoreService.executeWith(appCode, clientCode,
									kiRunFxDefinition.getNamespace(), kiRunFxDefinition.getName(),
									jsonObject.toString());
						}
						default -> {
							return Mono.empty();
						}
					}
				},

				response -> Mono.just(new ObjectWithUniqueID<>(response))

		).switchIfEmpty(this.messageResourceService.throwMessage(
				msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
				"{} : Unable to execute KIRunFunction: {} ", "URIService.executeKIRunFunction",
				kiRunFxDefinition.getNamespace() + kiRunFxDefinition.getName()));
	}
}
