package com.fincity.saas.ui.service;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.PathMatcher;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.commons.util.UniqueUtil;
import com.fincity.saas.ui.document.URIPath;
import com.fincity.saas.ui.enums.URIType;
import com.fincity.saas.ui.feign.IFeignCoreService;
import com.fincity.saas.ui.model.KIRunFxDefinition;
import com.fincity.saas.ui.model.RedirectionDefinition;
import com.fincity.saas.ui.repository.URIPathRepository;
import com.google.gson.JsonObject;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class URIPathService extends AbstractOverridableDataService<URIPath, URIPathRepository> {

	private static final String CACHE_NAME_URI = "URICache";

	private static final String CACHE_NAME_PATTERN = "URIPatternCache";

	private final IFeignCoreService iFeignCoreService;

	private final PathMatcher pathMatcher;

	public URIPathService(IFeignCoreService iFeignCoreService) {
		super(URIPath.class);
		this.iFeignCoreService = iFeignCoreService;
		this.pathMatcher = new AntPathMatcher();
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

				() -> this.validateURIPath(entity),

				valid -> super.update(entity),

				(valid, updatable) -> cacheService
						.evict(CACHE_NAME, updatable.getAppCode(), "-", updatable.getClientCode())
						.thenReturn(updatable)

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "URIService.update"));
	}

	@Override
	public Mono<Boolean> delete(String id) {

		return FlatMapUtil.flatMapMono(

				() -> this.read(id),

				uriPath -> super.delete(id),

				(uriPath, deleted) -> cacheService.evict(CACHE_NAME, uriPath.getAppCode(), "-", uriPath.getClientCode())
						.thenReturn(deleted)

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "URIService.delete"));
	}

	@Override
	public Mono<URIPath> create(URIPath entity) {

		if (entity.getUriType() == URIType.REDIRECTION) {
			if (entity.getRedirectionDefinition() == null)
				entity.setRedirectionDefinition(new RedirectionDefinition());

			entity.setKiRunFxDefinitions(null);
			RedirectionDefinition rd = entity.getRedirectionDefinition();

			if (StringUtil.safeIsBlank(entity.getName())) {

				if (StringUtil.safeIsBlank(rd.getShortCode()))
					rd.setShortCode(UniqueUtil.shortUUID());

				entity.setName("/" + rd.getShortCode());
			}
		} else {
			entity.setRedirectionDefinition(null);
		}

		if (StringUtil.safeIsBlank(entity.getName()))
			return this.messageResourceService.throwMessage(
					msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
					UIMessageResourceService.URI_STRING_NULL);

		return FlatMapUtil.flatMapMono(

				() -> this.validateURIPath(entity),

				valid -> super.create(entity),

				(valid, created) -> cacheService.evict(CACHE_NAME, created.getAppCode(), "-", created.getClientCode())
						.thenReturn(created)

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "URIService.create"));
	}

	private Mono<Boolean> validateURIPath(URIPath entity) {

		return FlatMapUtil.flatMapMono(

				() -> {

					if (StringUtil.safeIsBlank(entity.getPathString()))
						return Mono.just(true);

					boolean isValid = PathPatternParser.defaultInstance.parse(entity.getName())
							.matches(PathContainer.parsePath(entity.getPathString()));

					if (!isValid)
						return this.messageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
								UIMessageResourceService.URI_PATTERN_PATH_MISMATCH);

					return Mono.just(true);
				},

				valid -> {

					if (entity.getKiRunFxDefinitions() == null || entity.getKiRunFxDefinitions().isEmpty())
						return Mono.just(true);

					if (!entity.getUriType().equals(URIType.KIRUN_FUNCTION))
						return this.messageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
								UIMessageResourceService.URI_INVALID_TYPE);

					if (entity.getKiRunFxDefinitions().keySet().stream().anyMatch(e -> e != HttpMethod.GET
							&& e != HttpMethod.POST && e != HttpMethod.PUT && e != HttpMethod.PATCH
							&& e != HttpMethod.DELETE))
						return this.messageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
								UIMessageResourceService.URI_INVALID_METHOD);

					return Mono.just(true);
				},

				(valid, valid2) -> StringUtil.safeIsBlank(entity.getName()) ? this.messageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
						AbstractMongoMessageResourceService.NAME_MISSING) : Mono.just(true)

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "URIService.validateURIPath"));
	}

	@Override
	protected Mono<URIPath> updatableEntity(URIPath entity) {

		return FlatMapUtil.flatMapMono(

				() -> this.read(entity.getId()),

				existing -> {

					if (existing.getVersion() != entity.getVersion())
						return this.messageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
								AbstractMongoMessageResourceService.VERSION_MISMATCH);

					existing.setPathString(entity.getPathString());
					existing.setUriType(entity.getUriType());
					existing.setHeaders(entity.getHeaders());
					existing.setWhitelist(entity.getWhitelist());
					existing.setBlacklist(entity.getBlacklist());
					existing.setKiRunFxDefinitions(entity.getKiRunFxDefinitions());

					RedirectionDefinition rd = existing.getRedirectionDefinition();
					if (rd != null && entity.getRedirectionDefinition() != null) {

						RedirectionDefinition erd = entity.getRedirectionDefinition();
						rd.setRedirectionType(erd.getRedirectionType());
						rd.setTargetHttpMethod(erd.getTargetHttpMethod());
						rd.setTargetUrl(erd.getTargetUrl());
						rd.setValidFrom(erd.getValidFrom());
						rd.setValidUntil(erd.getValidUntil());
					}

					existing.setVersion(existing.getVersion() + 1);

					return Mono.just(existing);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "URIService.updatableEntity"));
	}

	public Mono<ObjectWithUniqueID<String>> generateApiDocs(String appCode, String clientCode) {

		return Mono.just("Coming soon...").map(ObjectWithUniqueID::new);
	}

	public Mono<String> getResponse(ServerHttpRequest request, JsonObject jsonObject,
			String appCode, String clientCode) {

		String uriPathString = request.getURI().getPath();

		return FlatMapUtil.flatMapMono(

				() -> this.findMatchingURIPath(uriPathString, appCode, clientCode),

				this::uriPathAccessCheck,

				(uriPath, hasAccess) -> switch (uriPath.getUriType()) {
					case KIRUN_FUNCTION -> executeKIRunFunction(request, jsonObject,
							uriPath, uriPath.getAppCode(), uriPath.getClientCode());

					case REDIRECTION -> // TODO
						Mono.empty();
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "URIService.getResponse"))
				.switchIfEmpty(Mono.empty());
	}

	public Mono<Boolean> uriPathAccessCheck(URIPath uriPath) {

		if (StringUtil.safeIsBlank(uriPath.getPermission()))
			return Mono.just(true);

		return SecurityContextUtil.hasAuthority(uriPath.getPermission())
				.filter(Boolean::booleanValue).switchIfEmpty(
						this.messageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
								AbstractMongoMessageResourceService.FORBIDDEN_EXECUTION, uriPath.getPermission()));
	}

	private Mono<URIPath> findMatchingURIPath(String uriPath, String appCode, String clientCode) {

		return FlatMapUtil.flatMapMono(

				() -> inheritanceService.order(appCode, clientCode, clientCode),

				clientCodes -> Flux.fromIterable(clientCodes)
						.flatMap(cc -> getURIPathPatternString(appCode, cc).flatMapMany(Flux::fromIterable))
						.filter(pattern -> pathMatcher.match(pattern, uriPath)).next(),

				(clientCodes, matchingPattern) -> super.read(matchingPattern, appCode, clientCode)
						.map(ObjectWithUniqueID::getObject))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "URIService.findMatchingURIPath"));
	}

	private Mono<List<String>> getURIPathPatternString(String appCode, String clientCode) {

		return cacheService.cacheEmptyValueOrGet(CACHE_NAME_PATTERN,
				() -> this.repo.findAllNamesByAppCodeAndClientCode(appCode, clientCode).collectList(),
				appCode, "-", clientCode);
	}

	private Mono<String> executeKIRunFunction(ServerHttpRequest request, JsonObject jsonObject,
			URIPath uriPath, String appCode, String clientCode) {

		HttpMethod iHttpStatus = request.getMethod();

		if (iHttpStatus == null || !uriPath.getKiRunFxDefinitions().containsKey(iHttpStatus)) {
			return Mono.empty();
		}

		KIRunFxDefinition kiRunFxDef = uriPath.getKiRunFxDefinitions().get(iHttpStatus);

		return switch (iHttpStatus) {
			case GET ->
				iFeignCoreService.executeWith(appCode, clientCode, kiRunFxDef.getNamespace(), kiRunFxDef.getName(),
						getParamsFromHeadersPathRequest(request, kiRunFxDef, uriPath));

			case POST, PUT, PATCH, DELETE ->
				iFeignCoreService.executeWith(appCode, clientCode, kiRunFxDef.getNamespace(),
						kiRunFxDef.getName(), jsonObject.toString());

			default -> Mono.empty();
		};
	}

	private MultiValueMap<String, String> getParamsFromHeadersPathRequest(ServerHttpRequest request,
			KIRunFxDefinition kiRunFxDefinition, URIPath uriPath) {

		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();

		this.addToParams(params, kiRunFxDefinition.getQueryParamMapping(), request.getQueryParams());

		if (!StringUtil.safeIsBlank(uriPath.getPathString())) {

			PathPattern.PathMatchInfo matchInfo = PathPatternParser.defaultInstance.parse(uriPath.getPathString())
					.matchAndExtract(PathContainer.parsePath(request.getURI().getPath()));

			if (matchInfo != null) {

				Map<String, String> iPathParams = matchInfo.getUriVariables();

				if (kiRunFxDefinition.getPathParamMapping() == null
						|| kiRunFxDefinition.getPathParamMapping().isEmpty()) {
					params.setAll(iPathParams);
				} else {
					kiRunFxDefinition.getPathParamMapping()
							.forEach((key, value) -> params.add(value, iPathParams.get(key)));
				}
			}
		}

		this.addToParams(params, kiRunFxDefinition.getHeadersMapping(), request.getHeaders());

		return params;
	}

	private void addToParams(MultiValueMap<String, String> params, Map<String, String> map,
			MultiValueMap<String, String> sourceMap) {

		if (map == null || map.isEmpty()) {
			params.addAll(sourceMap);
			return;
		}

		for (Map.Entry<String, String> entry : map.entrySet()) {
			params.add(entry.getValue(), sourceMap.getFirst(entry.getKey()));
		}
	}
}