package com.fincity.saas.ui.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

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

import com.fincity.nocode.kirun.engine.runtime.tokenextractors.ArgumentsTokenValueExtractor;
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
import com.fincity.saas.ui.model.PathDefinition;
import com.fincity.saas.ui.model.RedirectionDefinition;
import com.fincity.saas.ui.repository.URIPathRepository;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class URIPathService extends AbstractOverridableDataService<URIPath, URIPathRepository> {

	private static final String CACHE_NAME_URI = "URICache";

	private static final String CACHE_NAME_PATTERN = "URIPatternCache";

	private static final Set<String> ALLOWED_METHODS = Set.of(HttpMethod.GET.name(), HttpMethod.POST.name(),
			HttpMethod.PUT.name(), HttpMethod.PATCH.name(), HttpMethod.DELETE.name());

	private final IFeignCoreService iFeignCoreService;

	private final PathMatcher pathMatcher;

	private final Gson gson;

	private static final String FO_RESULT = "result";

	private static final String FO_NAME = "name";

	public URIPathService(IFeignCoreService iFeignCoreService) {
		super(URIPath.class);
		this.iFeignCoreService = iFeignCoreService;
		this.pathMatcher = new AntPathMatcher();
		gson = new Gson();
	}

	@Override
	public String getAccessCheckName() {
		return "Application";
	}

	@Override
	protected String getCollectionName() {
		return "uri_path";
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

				(valid, updatable) -> cacheService.evict(CACHE_NAME_PATTERN,
						updatable.getAppCode(), "-", updatable.getClientCode())
						.thenReturn(updatable)

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "URIService.update"));
	}

	@Override
	public Mono<Boolean> delete(String id) {

		return FlatMapUtil.flatMapMono(

				() -> this.read(id),

				uriPath -> super.delete(id),

				(uriPath, deleted) -> cacheService.evict(CACHE_NAME_PATTERN,
						uriPath.getAppCode(), "-", uriPath.getClientCode())
						.thenReturn(deleted)

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "URIService.delete"));
	}

	@Override
	public Mono<URIPath> create(URIPath entity) {

		if (StringUtil.safeIsBlank(entity.getName())) {
			if (StringUtil.safeIsBlank(entity.getShortCode()))
				entity.setShortCode(UniqueUtil.shortUUID());
			entity.setName("/" + entity.getShortCode());
		}

		if (StringUtil.safeIsBlank(entity.getName()) || StringUtil.safeIsBlank(entity.getPathString()))
			return this.messageResourceService.throwMessage(
					msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
					UIMessageResourceService.URI_STRING_NULL);

		return FlatMapUtil.flatMapMono(

				() -> this.setPathDefinitions(entity),

				this::validateURIPath,

				(pEntity, valid) -> super.create(pEntity),

				(pEntity, valid, created) -> cacheService.evict(CACHE_NAME_PATTERN,
						created.getAppCode(), "-", created.getClientCode())
						.thenReturn(created)

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "URIService.create"));
	}

	private Mono<URIPath> setPathDefinitions(URIPath entity) {

		for (Map.Entry<String, PathDefinition> pathDefs : entity.getPathDefinitions().entrySet()) {
			PathDefinition pathDef = pathDefs.getValue();
			if (pathDef.getUriType() == URIType.REDIRECTION) {
				if (pathDef.getRedirectionDefinition() == null)
					pathDef.setRedirectionDefinition(new RedirectionDefinition());
				// TODO : Add url short redirection;
				pathDef.setKiRunFxDefinition(null);
			} else {
				pathDef.setRedirectionDefinition(null);
			}
		}
		return Mono.just(entity).contextWrite(Context.of(LogUtil.METHOD_NAME, "URIPathService.setPathDefinitions"));
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

					for (Map.Entry<String, PathDefinition> pathDef : entity.getPathDefinitions().entrySet()) {
						if (!pathDef.getValue().isValidType())
							return this.messageResourceService.throwMessage(
									msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
									UIMessageResourceService.URI_INVALID_TYPE);
					}

					if (entity.getPathDefinitions().keySet().stream().noneMatch(ALLOWED_METHODS::contains))
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
					existing.setPathDefinitions(entity.getPathDefinitions());

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

				(uriPath, hasAccess) -> Mono.just(uriPath.getPathDefinitions()
						.getOrDefault(request.getMethod().name(), null)),

				(uriPath, hasAccess, pathDef) -> switch (pathDef.getUriType()) {
					case KIRUN_FUNCTION -> executeKIRunFunction(request, jsonObject, uriPath.getPathString(),
							pathDef.getKiRunFxDefinition(), uriPath.getAppCode(), clientCode);

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

	private Mono<String> executeKIRunFunction(ServerHttpRequest request, JsonObject jsonObject, String uriPathString,
			KIRunFxDefinition kiRunFxDef, String appCode, String clientCode) {

		return FlatMapUtil.flatMapMono(

				() -> switch (request.getMethod().toString()) {
					case "GET" -> iFeignCoreService.executeWith(appCode, clientCode, kiRunFxDef.getNamespace(),
							kiRunFxDef.getName(), getParamsFromHeadersPathRequest(request, uriPathString, kiRunFxDef));

					case "POST", "PUT", "PATCH", "DELETE" ->
						iFeignCoreService.executeWith(appCode, clientCode, kiRunFxDef.getNamespace(),
								kiRunFxDef.getName(), jsonObject.toString());

					default -> Mono.empty();
				},
				responseString -> {

					if (StringUtil.safeIsBlank(responseString))
						return Mono.just("");

					if (StringUtil.safeIsBlank(kiRunFxDef.getOutputEventName()))
						return Mono.just(responseString);

					JsonArray response = this.gson.fromJson(responseString, JsonArray.class);

					return extractOutputEvent(response, kiRunFxDef.getOutputEventName(),
							kiRunFxDef.getOutputEventParamName());
				});
	}

	private Mono<String> extractOutputEvent(JsonArray response, String outputEventName,
			String outputEventParamName) {

		return FlatMapUtil.flatMapMono(

				() -> findMatchingOutputEvent(response, outputEventName),
				matchingOutput -> {

					if (StringUtil.safeIsBlank(outputEventParamName))
						return Mono.justOrEmpty(matchingOutput.get(FO_RESULT).toString());

					if (matchingOutput.has(FO_RESULT)
							&& matchingOutput.get(FO_RESULT).isJsonObject()) {

						ArgumentsTokenValueExtractor atv = new ArgumentsTokenValueExtractor(
								Map.of(FO_RESULT, matchingOutput.get(FO_RESULT)));

						return Mono.justOrEmpty(atv.getValue(ArgumentsTokenValueExtractor.PREFIX
								+ outputEventParamName).toString());
					}
					return Mono.empty();

				}).switchIfEmpty(Mono.empty());
	}

	private Mono<JsonObject> findMatchingOutputEvent(JsonArray response, String outputEventName) {
		return Flux.fromIterable(response)
				.filter(JsonElement::isJsonObject)
				.map(JsonElement::getAsJsonObject)
				.filter(output -> output.has(FO_NAME) &&
						output.get(FO_NAME).isJsonPrimitive() &&
						outputEventName.equals(output.get(FO_NAME).getAsString()))
				.next();
	}

	private MultiValueMap<String, String> getParamsFromHeadersPathRequest(ServerHttpRequest request,
			String uriPathString, KIRunFxDefinition kiRunFxDefinition) {

		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();

		this.addToParams(params, kiRunFxDefinition.getQueryParamMapping(), request.getQueryParams());

		if (!StringUtil.safeIsBlank(uriPathString)) {

			PathPattern.PathMatchInfo matchInfo = PathPatternParser.defaultInstance.parse(uriPathString)
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

		map.forEach((key, value) -> params.add(value, sourceMap.getFirst(key)));
	}
}
