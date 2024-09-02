package com.fincity.saas.ui.service;

import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
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

	private static final String CACHE_NAME_URI = "URICache";

	private static final String URI_READ_ACTION = "_URI_READ";

	private IFeignCoreService iFeignCoreService;

	@Autowired
	public URIService(IFeignCoreService iFeignCoreService) {
		super(URI.class);
		this.iFeignCoreService = iFeignCoreService;
	}

	protected URIService() {
		super(URI.class);
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
	public Mono<URI> update(URI entity) {

		return FlatMapUtil.flatMapMono(

				() -> getValidURI(entity),

				vuri -> super.update(entity).flatMap(this.cacheService
						.evictAllFunction(this.getCacheName(entity.getAppCode(), URI_READ_ACTION)))
		).contextWrite(Context.of(LogUtil.METHOD_NAME, "URIService.update"));
	}

	@Override
	public Mono<URI> create(URI entity) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> getValidURI(entity),

				(ca, vuri) -> {
					if (StringUtil.safeIsBlank(vuri.getClientCode())) {
						vuri.setClientCode(ca.getClientCode());
					}

					if (StringUtil.safeIsBlank(vuri.getAppCode())) {
						vuri.setAppCode(ca.getUrlAppCode());
					}
					return super.create(vuri);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "URIService.create"));
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
					URI uri = ouri.getObject();
					switch (uri.getUrlType()) {
						case KIRUN_FUNCTION -> {
							return executeKIRunFunction(request, jsonObject, uri.getKiRunFxDefinition(),
									uri.getAppCode(), uri.getClientCode());
						}
						case REDIRECTION, SHORTENED_URL -> {
							// TODO
							return Mono.empty();
						}
						default -> {
							return Mono.empty();
						}
					}
				}).switchIfEmpty(Mono.empty());

	}

	private Mono<ObjectWithUniqueID<URI>> readByUrlString(String uriPath, String appCode, String clientCode) {

		return FlatMapUtil.flatMapMono(

				() -> cacheService.cacheValueOrGet(this.getCacheName(appCode, URI_READ_ACTION),
						() -> this.repo.findByPathAndAppCodeAndClientCode(uriPath, appCode, clientCode),
						getCacheKeys(uriPath, appCode, clientCode)),

				uri -> {

					if (StringUtil.safeIsBlank(uri.getPermission())) {
						return Mono.just(new ObjectWithUniqueID<>(uri))
								.contextWrite(Context.of(LogUtil.METHOD_NAME, "URIService.read [Open Url Read]"));
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
							return iFeignCoreService.executeWith(appCode, clientCode, kiRunFxDefinition.getNamespace(),
									kiRunFxDefinition.getName(), request);
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
						.contextWrite(Context.of(LogUtil.METHOD_NAME, "URIService.getResponse [KIRun Function Response]"))

		).switchIfEmpty(this.messageResourceService.throwMessage(
				msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
				"URIService.getResponse.executeKIRunFunction : Unable to execute KIRunFunction : {} ",
				kiRunFxDefinition.getNamespace() + "." + kiRunFxDefinition.getName()));
	}

	private Object[] getCacheKeys(String uriString, String appCode, String clientCode) {
		return new Object[] { appCode, ":", clientCode, ":", uriString };
	}

	private Mono<URI> getValidURI(URI uri) {

		if (StringUtil.safeIsBlank(uri.getUriString()))
			return this.messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
					UIMessageResourceService.URI_STRING_NULL);

		try {
			return Mono.just(fillURIInfo(uri));
		} catch (URISyntaxException e) {
			return this.messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
					UIMessageResourceService.URI_STRING_INVALID);
		}
	}

	private URI fillURIInfo(URI uri) throws URISyntaxException {

		java.net.URI jUri = new java.net.URI(uri.getUriString());

		uri.setUriString(jUri.toString());
		uri.setScheme(jUri.getScheme());
		uri.setFragment(jUri.getFragment());
		uri.setAuthority(jUri.getAuthority());
		uri.setUserInfo(jUri.getUserInfo());
		uri.setHost(jUri.getHost());
		uri.setPort(jUri.getPort());
		uri.setPath(getValidPath(jUri.getPath()));
		uri.setQuery(jUri.getQuery());
		uri.setQueryParams(jUri.getQuery() != null ? getQueryParams(jUri.getQuery()) : null);

		return uri;
	}

	private static Map<String, String> getQueryParams(String queryParams) {
		return Arrays.stream(queryParams.split("&"))
				.map(param -> param.split("=", 2))
				.collect(Collectors.toMap(
						pair -> pair[0],
						pair -> pair.length > 1 ? pair[1] : ""));
	}

	private static String getValidPath(String path) {
		return path.startsWith("/") ? path : "/" + path;
	}
}
