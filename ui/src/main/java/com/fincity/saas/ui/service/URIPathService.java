package com.fincity.saas.ui.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

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
import com.fincity.saas.ui.repository.URIPathRepository;
import com.fincity.saas.ui.utils.URIPathBuilder;
import com.fincity.saas.ui.utils.URIPathParser;
import com.google.gson.JsonObject;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuples;

@Service
public class URIPathService extends AbstractOverridableDataService<URIPath, URIPathRepository> {

	private static final String CACHE_NAME_URI = "URICache";

	private static final String URI_PATTERN_READ_ACTION = "_URI_PATTERN_READ";

	private IFeignCoreService iFeignCoreService;

	private PathMatcher pathMatcher;

	@Autowired
	public URIPathService(IFeignCoreService iFeignCoreService) {
		super(URIPath.class);
		this.iFeignCoreService = iFeignCoreService;
		this.pathMatcher = new AntPathMatcher();
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
						.evictAllFunction(super.getCacheName(entity.getAppCode(), entity.getName())))

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "URIService.update"));
	}

	@Override
	public Mono<Boolean> delete(String id) {

		return FlatMapUtil.flatMapMono(

				() -> this.read(id),

				uriPath -> super.delete(id).flatMap(this.cacheService
						.evictAllFunction(super.getCacheName(uriPath.getAppCode(), uriPath.getName()))),

				(uriPath, deleted) -> evictCacheUriPathPattern(uriPath)

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "URIService.delete"));
	}

	@Override
	public Mono<URIPath> create(URIPath entity) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> {

					if (StringUtil.safeIsBlank(entity.getName()))
						return this.messageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
								UIMessageResourceService.URI_STRING_NULL);

					return getValidURI(entity);
				},

				(ca, uriPath) -> {
					if (StringUtil.safeIsBlank(uriPath.getClientCode())) {
						uriPath.setClientCode(ca.getClientCode());
					}

					if (StringUtil.safeIsBlank(uriPath.getAppCode())) {
						uriPath.setAppCode(ca.getUrlAppCode());
					}

					return super.create(uriPath).flatMap(this.cacheService
							.evictFunction(getCacheName(uriPath.getAppCode(), URI_PATTERN_READ_ACTION),
									getUriPatternCacheKey(uriPath.getAppCode(), uriPath.getClientCode())));
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "URIService.create"));
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

					existing.setPathString(entity.getPathString());
					existing.setUriType(entity.getUriType());
					existing.setIsAuthenticated(entity.getIsAuthenticated());
					existing.setHttpMethods(entity.getHttpMethods());
					existing.setHeaders(entity.getHeaders());
					existing.setQueryParams(entity.getQueryParams());
					existing.setWhitelist(entity.getWhitelist());
					existing.setBlacklist(entity.getBlacklist());
					existing.setKiRunFxDefinition(entity.getKiRunFxDefinition());
					existing.setPermission(entity.getPermission());

					existing.setVersion(existing.getVersion() + 1);

					return Mono.just(existing);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "URIService.updatableEntity"));
	}

	public Mono<ObjectWithUniqueID<String>> getResponse(ServerHttpRequest request, JsonObject jsonObject,
			String appCode, String clientCode) {

		String uriPathString = request.getURI().getPath();

		return FlatMapUtil.flatMapMono(

				() -> this.readByUriPathString(uriPathString, appCode, clientCode),

				uqUriPath -> {
					URIPath uriPath = uqUriPath.getObject();
					return switch (uriPath.getUriType()) {
						case KIRUN_FUNCTION -> executeKIRunFunction(request, jsonObject, uriPath.getKiRunFxDefinition(),
								uriPath, uriPath.getAppCode(), uriPath.getClientCode());

						case REDIRECTION -> // TODO
							Mono.empty();
					};
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "URIService.getResponse"))
				.switchIfEmpty(Mono.empty());
	}

	private Object[] getUriPatternCacheKey(String appCode, String clientCode) {
		return new Object[] { clientCode, ":", appCode };
	}

	private Mono<Boolean> evictCacheUriPathPattern(URIPath uriPath) {

		return FlatMapUtil.flatMapMono(

				() -> cacheService.<List<String>>get(getCacheName(uriPath.getAppCode(), URI_PATTERN_READ_ACTION),
						getUriPatternCacheKey(uriPath.getAppCode(), uriPath.getClientCode())),

				cacheUriPaths -> {
					if (cacheUriPaths == null || !cacheUriPaths.contains(uriPath.getName())) {
						return Mono.just(true);
					}

					cacheUriPaths.remove(uriPath.getName());
					return cacheService
							.put(getCacheName(uriPath.getAppCode(), URI_PATTERN_READ_ACTION), cacheUriPaths,
									getUriPatternCacheKey(uriPath.getAppCode(), uriPath.getClientCode()))
							.thenReturn(true);
				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "URIService.evictCacheUriPathPattern"));
	}

	private Mono<ObjectWithUniqueID<URIPath>> readByUriPathString(String uriPathString, String appCode,
			String clientCode) {

		return FlatMapUtil.flatMapMono(

				() -> findMatchingURIPath(uriPathString, appCode, clientCode),

				uqUriPath -> {
					if (StringUtil.safeIsBlank(uqUriPath.getObject().getPermission())) {
						return Mono.just(uqUriPath)
								.contextWrite(Context.of(LogUtil.METHOD_NAME, "URIService.read [Open Url Read]"));
					}

					return FlatMapUtil.flatMapMono(

							SecurityContextUtil::getUsersContextAuthentication,

							ca -> Mono.just(ca.isAuthenticated()),

							(ca, isAuthenticated) -> Boolean.TRUE.equals(isAuthenticated)
									? Mono.just(uqUriPath)
									: Mono.empty())
							.contextWrite(Context.of(LogUtil.METHOD_NAME, "URIService.read [Authenticated Url read]"));
				}).switchIfEmpty(Mono.empty());
	}

	private Mono<ObjectWithUniqueID<URIPath>> findMatchingURIPath(String uriPath, String appCode, String clientCode) {

		return FlatMapUtil.flatMapMono(

				() -> inheritanceService.order(appCode, clientCode, clientCode),

				clientCodes -> FlatMapUtil.flatMapFlux(
						() -> Flux.fromIterable(clientCodes),

						code -> getURIPathPatternString(appCode, code)
								.flatMapMany(paths -> Flux.fromIterable(paths)
										.filter(pattern -> matchesPattern(uriPath, pattern))
										.map(pattern -> Tuples.of(code, pattern))))
						.next(),

				(clientCodes, matchingPattern) -> Optional.ofNullable(matchingPattern)
						.map(pattern -> super.read(pattern.getT2(), appCode, pattern.getT1()))
						.orElse(Mono.empty()))
				.switchIfEmpty(Mono.empty());
	}

	private Mono<List<String>> getURIPathPatternString(String appCode, String clientCode) {

		return cacheService.cacheEmptyValueOrGet(this.getCacheName(appCode, URI_PATTERN_READ_ACTION),
				() -> this.repo.findAllNamesByAppCodeAndClientCode(appCode, clientCode).collectList(),
				getUriPatternCacheKey(appCode, clientCode));
	}

	private boolean matchesPattern(String uriPath, String pattern) {

		if (uriPath == null || pattern == null) {
			return false;
		}

		return pathMatcher.match(pattern, uriPath);
	}

	private Mono<ObjectWithUniqueID<String>> executeKIRunFunction(ServerHttpRequest request, JsonObject jsonObject,
			KIRunFxDefinition kiRunFxDefinition, URIPath uriPath, String appCode, String clientCode) {

		return FlatMapUtil.flatMapMono(
				() -> {
					if (request.getMethod() == null) {
						return Mono.empty();
					}

					if (!uriPath.getHttpMethods().contains(request.getMethod())) {
						return Mono.empty();
					}

					return Mono.just(request.getMethod());
				},
				httpMethod -> {
					switch (httpMethod) {
						case GET -> {
							URIPathBuilder uriPathBuilder = getURIPathForKIRunFx(request, kiRunFxDefinition, uriPath);
							return iFeignCoreService.executeWith(appCode, clientCode, kiRunFxDefinition.getNamespace(),
									kiRunFxDefinition.getName(), uriPathBuilder.extractAllParams())
									.map(ObjectWithUniqueID::new);
						}
						case HEAD -> {
							URIPathBuilder uriPathBuilder = getURIPathForKIRunFx(request, kiRunFxDefinition, uriPath);
							return iFeignCoreService.executeWith(appCode, clientCode, kiRunFxDefinition.getNamespace(),
									kiRunFxDefinition.getName(), uriPathBuilder.extractAllParams())
									.map(response -> new ObjectWithUniqueID<>(""));
						}
						case POST, PUT, PATCH, DELETE -> {
							return iFeignCoreService.executeWith(appCode, clientCode, kiRunFxDefinition.getNamespace(),
									kiRunFxDefinition.getName(), jsonObject.toString())
									.map(ObjectWithUniqueID::new);
						}
						default -> {
							return Mono.empty();
						}
					}
				}
		).switchIfEmpty(this.messageResourceService.throwMessage(
				msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
				UIMessageResourceService.UNABLE_TO_RUN_KIRUN_FX,
				kiRunFxDefinition.getNamespace() + "." + kiRunFxDefinition.getName()));
	}

	private Mono<URIPath> getValidURI(URIPath uriPath) {

		if (!pathMatcher.match(uriPath.getName(), uriPath.getPathString())) {
			return this.messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
					UIMessageResourceService.URI_PATTERN_PATH_MISMATCH, uriPath.getName(), uriPath.getPathString());
		}

		uriPath.setPathString(
				URIPathParser.parse(uriPath.getPathString()).extractPath().normalizeAndValidate().build());

		if (Boolean.FALSE.equals(checkKIRunFxParameters(uriPath))) {
			return this.messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
					UIMessageResourceService.URI_STRING_PARAMETERS_INVALID);
		}

		return Mono.just(uriPath);
	}

	private Boolean checkKIRunFxParameters(URIPath uriPath) {

		if (!URIType.KIRUN_FUNCTION.equals(uriPath.getUriType())) {
			return true;
		}

		KIRunFxDefinition kiRunFxDefinition = uriPath.getKiRunFxDefinition();

		if (kiRunFxDefinition == null) {
			return false;
		}

		if (kiRunFxDefinition.getHeadersMapping() != null &&
				!uriPath.getHeaders().containsAll(kiRunFxDefinition.getHeadersMapping().keySet())) {
			return false;
		}

		if (kiRunFxDefinition.getPathParamMapping() != null &&
				!URIPathParser.extractPathParams(uriPath.getPathString())
						.containsAll(kiRunFxDefinition.getPathParamMapping().keySet())) {
			return false;
		}

		return kiRunFxDefinition.getQueryParamMapping() == null ||
				uriPath.getQueryParams().containsAll(kiRunFxDefinition.getQueryParamMapping().keySet());
	}

	private URIPathBuilder getURIPathForKIRunFx(ServerHttpRequest request, KIRunFxDefinition kiRunFxDefinition,
			URIPath uriPath) {

		String requestPath = request.getURI().getPath();

		Map<String, String> iHeaders = request.getHeaders().toSingleValueMap();

		Map<String, String> iQueryParams = request.getQueryParams().toSingleValueMap();

		Map<String, String> iPathParams = pathMatcher.extractUriTemplateVariables(uriPath.getPathString(), requestPath);

		return URIPathBuilder.buildPath(URIPathParser.extractJustPath(uriPath.getPathString()))
				.addQueryParams(iPathParams, kiRunFxDefinition.getPathParamMapping())
				.addQueryParams(iQueryParams, kiRunFxDefinition.getQueryParamMapping())
				.addQueryParams(iHeaders, kiRunFxDefinition.getHeadersMapping());
	}
}
