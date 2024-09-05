package com.fincity.saas.ui.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.ui.document.URIPath;
import com.fincity.saas.ui.enums.URIType;
import com.fincity.saas.ui.feign.IFeignCoreService;
import com.fincity.saas.ui.model.KIRunFxDefinition;
import com.fincity.saas.ui.model.PathDefinition;
import com.fincity.saas.ui.repository.URIPathRepository;
import com.fincity.saas.ui.utils.URIPathBuilder;
import com.google.gson.JsonObject;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class URIPathService extends AbstractOverridableDataService<URIPath, URIPathRepository> {

	private static final String CACHE_NAME_URI = "URICache";

	private static final String URI_READ_ACTION = "_URI_READ";

	private IFeignCoreService iFeignCoreService;

	@Autowired
	public URIPathService(IFeignCoreService iFeignCoreService) {
		super(URIPath.class);
		this.iFeignCoreService = iFeignCoreService;
	}

	protected URIPathService() {
		super(URIPath.class);
	}

	@Override
	public String getAccessCheckName() {
		return "Application";
	}

	@Override
	public String getCacheName(String appCode, String cacheAction) {
		return super.getCacheName(appCode + "_" + CACHE_NAME_URI, appCode) + cacheAction;
	}

	@Override
	public Mono<URIPath> update(URIPath entity) {

		return FlatMapUtil.flatMapMono(

				() -> getValidURI(entity),

				uriPath -> super.update(entity).flatMap(this.cacheService
						.evictAllFunction(this.getCacheName(entity.getAppCode(), URI_READ_ACTION))))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "URIService.update"));
	}

	@Override
	public Mono<Boolean> delete(String id) {
		return this.read(id).flatMap(entity -> super.delete(id).flatMap(this.cacheService
				.evictAllFunction(this.getCacheName(entity.getAppCode(), URI_READ_ACTION))))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "URIService.delete"));
	}

	@Override
	public Mono<URIPath> create(URIPath entity) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> getValidURI(entity),

				(ca, uriPath) -> {
					if (StringUtil.safeIsBlank(uriPath.getClientCode())) {
						uriPath.setClientCode(ca.getClientCode());
					}

					if (StringUtil.safeIsBlank(uriPath.getAppCode())) {
						uriPath.setAppCode(ca.getUrlAppCode());
					}

					return Mono.just(uriPath);
				},

				(ca, uriPath, vuri) -> super.create(vuri).flatMap(this.cacheService
						.evictAllFunction(this.getCacheName(entity.getAppCode(), URI_READ_ACTION))))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "URIService.create"));
	}

	@Override
	protected Mono<URIPath> updatableEntity(URIPath entity) {

		return FlatMapUtil.flatMapMono(

				() -> this.read(entity.getId()),

				existing -> {

					if (existing.getVersion() != entity.getVersion()) {
						return this.messageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
								AbstractMongoMessageResourceService.VERSION_MISMATCH);
					}

					existing.setName(entity.getName());
					existing.setPathDefinition(entity.getPathDefinition());
					existing.setUriType(entity.getUriType());
					existing.setWhitelist(entity.getWhitelist());
					existing.setBlacklist(entity.getBlacklist());
					existing.setKiRunFxDefinition(entity.getKiRunFxDefinition());
					existing.setRedirectionDefinitions(entity.getRedirectionDefinitions());
					existing.setPermission(entity.getPermission());

					existing.setVersion(existing.getVersion() + 1);

					return Mono.just(existing);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "URIService.updatableEntity"));
	}

	public Mono<ObjectWithUniqueID<String>> getResponse(ServerHttpRequest request, JsonObject jsonObject,
			String appCode, String clientCode) {

		String uriPath = request.getURI().getPath();

		return FlatMapUtil.flatMapMono(

				() -> this.readByUrlString(uriPath, appCode, clientCode),

				ouri -> {
					URIPath uri = ouri.getObject();
					switch (uri.getUriType()) {
						case KIRUN_FUNCTION -> {
							return executeKIRunFunction(request, jsonObject, uri.getKiRunFxDefinition(),
									uri.getPathDefinition(), uri.getAppCode(), uri.getClientCode());
						}
						case REDIRECTION, SHORTENED_URL -> {
							// TODO
							return Mono.empty();
						}
						default -> {
							return Mono.empty();
						}
					}
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "URIService.getResponse"))
				.switchIfEmpty(Mono.empty());

	}

	private String getCacheKey(String appCode, String clientCode) {
		return appCode + ":" + clientCode;
	}

	private Mono<ObjectWithUniqueID<URIPath>> readByUrlString(String uriPath, String appCode, String clientCode) {

		return FlatMapUtil.flatMapMono(

				() -> findMatchingURIPath(uriPath, appCode, clientCode),

				ouuri -> {

					if (StringUtil.safeIsBlank(ouuri.getObject().getPermission())) {
						return Mono.just(ouuri)
								.contextWrite(Context.of(LogUtil.METHOD_NAME, "URIService.read [Open Url Read]"));
					}

					return FlatMapUtil.flatMapMono(

							SecurityContextUtil::getUsersContextAuthentication,

							ca -> Mono.just(ca.isAuthenticated()),

							(ca, isAuthenticated) -> Boolean.TRUE.equals(isAuthenticated)
									? Mono.just(ouuri)
									: Mono.empty())
							.contextWrite(Context.of(LogUtil.METHOD_NAME, "URIService.read [Authenticated Url read]"));
				}).switchIfEmpty(Mono.empty());
	}

	private Mono<ObjectWithUniqueID<URIPath>> findMatchingURIPath(String uriPath, String appCode, String clientCode) {

		return FlatMapUtil.flatMapMono(

				() -> cacheService.cacheValueOrGet(this.getCacheName(appCode, URI_READ_ACTION),
						() -> this.repo.findByAppCodeAndClientCode(appCode, clientCode).collectList(),
						getCacheKey(appCode, clientCode)),

				uriPaths -> Mono.justOrEmpty(uriPaths.stream().filter(uri -> matchesPattern(uriPath, uri.getName()))
						.findFirst()),

				(uriPaths, matchingUriPath) -> Mono.just(new ObjectWithUniqueID<>(matchingUriPath)))
				.switchIfEmpty(Mono.empty());
	}

	private boolean matchesPattern(String uriPath, String pattern) {
		if (uriPath == null || pattern == null) {
			return false;
		}

		AntPathMatcher pathMatcher = new AntPathMatcher();

		return pathMatcher.match(pattern, uriPath);
	}

	private Mono<ObjectWithUniqueID<String>> executeKIRunFunction(ServerHttpRequest request, JsonObject jsonObject,
			KIRunFxDefinition kiRunFxDefinition, PathDefinition pathDefinition, String appCode, String clientCode) {

		return FlatMapUtil.flatMapMono(
				() -> {
					switch (request.getMethod()) {
						case GET, HEAD -> {
							URIPathBuilder uriPathBuilder = getURIPathForKIRunFx(request, kiRunFxDefinition,
									pathDefinition);
							return iFeignCoreService.executeWith(appCode, clientCode, kiRunFxDefinition.getNamespace(),
									kiRunFxDefinition.getName(), uriPathBuilder.extractAllParams());
						}
						case POST, PUT, PATCH, DELETE -> {
							return iFeignCoreService.executeWith(appCode, clientCode, kiRunFxDefinition.getNamespace(),
									kiRunFxDefinition.getName(), jsonObject.toString());
						}
						default -> {
							return Mono.empty();
						}
					}
				},

				response -> Mono.just(new ObjectWithUniqueID<>(response))

		).switchIfEmpty(this.messageResourceService.throwMessage(
				msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
				"URIService.getResponse.executeKIRunFunction : Unable to execute KIRunFunction : {} ",
				kiRunFxDefinition.getNamespace() + "." + kiRunFxDefinition.getName()));
	}

	private Mono<URIPath> getValidURI(URIPath uriPath) {

		if (StringUtil.safeIsBlank(uriPath.getName()))
			return this.messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
					UIMessageResourceService.URI_STRING_NULL);

		URIPath validUriPath = fillURIInfo(uriPath);

		if (Boolean.FALSE.equals(checkKIRunFxParameters(validUriPath))) {
			return this.messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
					UIMessageResourceService.URI_STRING_PARAMETERS_INVALID);
		}

		return Mono.just(validUriPath);
	}

	private URIPath fillURIInfo(URIPath uriPath) {

		PathDefinition pathDefinition = new PathDefinition(uriPath.getName());

		uriPath.setName(pathDefinition.getPathPattern());
		uriPath.setPathDefinition(pathDefinition);

		return uriPath;
	}

	private Boolean checkKIRunFxParameters(URIPath uriPath) {

		if (uriPath.getUriType().equals(URIType.KIRUN_FUNCTION) && uriPath.getKiRunFxDefinition() == null) {
			return false;
		}

		KIRunFxDefinition kiRunFxDefinition = uriPath.getKiRunFxDefinition();

		if (kiRunFxDefinition != null) {

			if (kiRunFxDefinition.getHeadersMapping() != null && !uriPath.getHeaders()
					.containsAll(kiRunFxDefinition.getHeadersMapping().keySet())) {
				return false;
			}

			if (kiRunFxDefinition.getPathParamMapping() != null && !uriPath.getPathDefinition().getPathParams()
					.containsAll(kiRunFxDefinition.getPathParamMapping().keySet())) {
				return false;
			}

			return kiRunFxDefinition.getQueryParamMapping() == null || uriPath.getPathDefinition().getQueryParams()
					.containsAll(kiRunFxDefinition.getQueryParamMapping().keySet());
		}
		return true;
	}

	private URIPathBuilder getURIPathForKIRunFx(ServerHttpRequest request, KIRunFxDefinition kiRunFxDefinition,
			PathDefinition pathDefinition) {

		if (pathDefinition == null) {
			return URIPathBuilder.buildURI("");
		}

		if (kiRunFxDefinition == null) {
			return URIPathBuilder.buildURI(pathDefinition.getJustPath());
		}

		Map<String, String> iHeaders = request.getHeaders().toSingleValueMap();

		Map<String, String> iQueryParams = request.getQueryParams().toSingleValueMap();

		Map<String, String> iPathParams = pathDefinition.extractPathParameters(request.getURI().getPath());

		return URIPathBuilder.buildURI(pathDefinition.getJustPath())
				.addQueryParams(iQueryParams, kiRunFxDefinition.getQueryParamMapping())
				.addQueryParams(iPathParams, kiRunFxDefinition.getPathParamMapping())
				.addQueryParams(iHeaders, kiRunFxDefinition.getHeadersMapping());
	}
}
