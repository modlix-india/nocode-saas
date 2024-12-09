package com.fincity.saas.ui.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.ui.service.IndexHTMLService;
import com.fincity.saas.ui.service.JSService;
import com.fincity.saas.ui.service.ManifestService;
import com.fincity.saas.ui.service.URIPathService;
import com.fincity.saas.ui.utils.ResponseEntityUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import reactor.core.publisher.Mono;

@RestController
public class UniversalController {

	private final JSService jsService;

	private final IndexHTMLService indexHTMLService;

	private final ManifestService manifestService;

	private final URIPathService uriPathService;

	private final IFeignSecurityService securityService;

	private final Gson gson;

	@Value("${ui.resourceCacheAge:604800}")
	private int cacheAge;

	private static final ResponseEntity<String> RESPONSE_NOT_FOUND = ResponseEntity
			.notFound()
			.build();

	private static final ResponseEntity<String> RESPONSE_BAD_REQUEST = ResponseEntity
			.badRequest()
			.build();

	public UniversalController(JSService jsService, IndexHTMLService indexHTMLService, ManifestService manifestService,
			URIPathService uriPathService, IFeignSecurityService securityService, Gson gson) {
		this.jsService = jsService;
		this.indexHTMLService = indexHTMLService;
		this.manifestService = manifestService;
		this.uriPathService = uriPathService;
		this.securityService = securityService;
		this.gson = gson;
	}

	@GetMapping(value = "js/dist/**")
	public Mono<ResponseEntity<String>> indexJS(@RequestHeader(name = "If-None-Match", required = false) String eTag,
			ServerHttpRequest request) {

		int index = request.getURI().getPath().indexOf("/js/dist/");
		String filePath = request.getURI().getPath().substring(index + 9);

		return jsService.getJSResource(filePath)
				.flatMap(e -> ResponseEntityUtils.makeResponseEntity(e, eTag, cacheAge))
				.defaultIfEmpty(RESPONSE_NOT_FOUND);
	}

	@GetMapping(value = "manifest/manifest.json", produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
	public Mono<ResponseEntity<String>> manifest(@RequestHeader("appCode") String appCode,
			@RequestHeader("clientCode") String clientCode,
			@RequestHeader(name = "If-None-Match", required = false) String eTag) {

		return manifestService.getManifest(appCode, clientCode)
				.flatMap(e -> ResponseEntityUtils.makeResponseEntity(e, eTag, cacheAge))
				.defaultIfEmpty(RESPONSE_NOT_FOUND);
	}

	@GetMapping(value = "/apiDocs", produces = MimeTypeUtils.TEXT_HTML_VALUE)
	public Mono<ResponseEntity<String>> apiDocs(@RequestHeader("appCode") String appCode,
			@RequestHeader("clientCode") String clientCode,
			@RequestHeader(name = "If-None-Match", required = false) String eTag) {

		return uriPathService.generateApiDocs(appCode, clientCode)
				.flatMap(e -> ResponseEntityUtils.makeResponseEntity(e, eTag, cacheAge))
				.defaultIfEmpty(RESPONSE_NOT_FOUND);
	}

	@GetMapping(value = "**")
	public Mono<ResponseEntity<String>> defaultGetRequest(
			@RequestHeader("appCode") String appCode,
			@RequestHeader("clientCode") String clientCode,
			@RequestHeader(name = "If-None-Match", required = false) String eTag,
			@RequestParam(required = false) String debug,
			ServerHttpRequest request) {

		return FlatMapUtil.flatMapMono(
				SecurityContextUtil::getUsersContextAuthentication,

				ca -> ca.isAuthenticated() ? Mono.just(ca.getClientCode()) : Mono.just(clientCode),

				(ca, cc) -> uriPathService.getResponse(request, null, appCode, cc).map(ResponseEntity::ok))
				.switchIfEmpty(Mono
						.defer(() -> indexHTMLService.getIndexHTML(appCode, clientCode, debug)
								.flatMap(e -> ResponseEntityUtils
										.makeResponseEntity(e, eTag, cacheAge, MimeTypeUtils.TEXT_HTML_VALUE))));
	}

	@RequestMapping(value = "**", produces = MimeTypeUtils.APPLICATION_JSON_VALUE, method = { RequestMethod.POST,
			RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE })
	public Mono<ResponseEntity<String>> defaultRequests(
			@RequestHeader("appCode") String appCode,
			@RequestHeader("clientCode") String clientCode,
			@RequestHeader(name = "If-None-Match", required = false) String eTag,
			ServerHttpRequest request,
			@RequestBody String jsonString) {

		JsonObject jsonObject = StringUtil.safeIsBlank(jsonString) ? new JsonObject()
				: this.gson.fromJson(jsonString, JsonObject.class);

		return FlatMapUtil.flatMapMono(
				SecurityContextUtil::getUsersContextAuthentication,

				ca -> ca.isAuthenticated() ? Mono.just(ca.getClientCode()) : Mono.just(clientCode),

				(ca, cc) -> uriPathService.getResponse(request, jsonObject, appCode, cc).map(ResponseEntity::ok))
				.switchIfEmpty(Mono.just(RESPONSE_BAD_REQUEST));
	}

	@GetMapping("/.well-known/acme-challenge/{token}")
	public Mono<ResponseEntity<String>> tokenCheck(@PathVariable String token) {

		return this.securityService.token(token).map(ResponseEntity::ok);
	}
}
