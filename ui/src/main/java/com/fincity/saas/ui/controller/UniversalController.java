package com.fincity.saas.ui.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.ui.service.IndexHTMLService;
import com.fincity.saas.ui.service.JSService;
import com.fincity.saas.ui.service.ManifestService;
import com.fincity.saas.ui.service.URIService;
import com.fincity.saas.ui.utils.ResponseEntityUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import reactor.core.publisher.Mono;

@RestController
public class UniversalController {

	private final JSService jsService;

	private final IndexHTMLService indexHTMLService;

	private final ManifestService manifestService;

	private final URIService uriService;

	private final IFeignSecurityService securityService;

	private final Gson gson;

	@Value("${ui.jsURL:}")
	private String jsURL;

	@Value("${ui.resourceCacheAge:604800}")
	private int cacheAge;

	private static final ResponseEntity<String> RESPONSE_NOT_FOUND = ResponseEntity
			.notFound()
			.build();

	public UniversalController(JSService jsService, IndexHTMLService indexHTMLService, ManifestService manifestService,
			URIService uriService, IFeignSecurityService securityService, Gson gson) {
		this.jsService = jsService;
		this.indexHTMLService = indexHTMLService;
		this.manifestService = manifestService;
		this.uriService = uriService;
		this.securityService = securityService;
		this.gson = gson;
	}

	@GetMapping(value = "js/index.js", produces = "text/javascript")
	public Mono<ResponseEntity<String>> indexJS(@RequestHeader(name = "If-None-Match", required = false) String eTag) {

		return jsService.getJSObject()
				.flatMap(e -> ResponseEntityUtils.makeResponseEntity(e, eTag, cacheAge))
				.defaultIfEmpty(RESPONSE_NOT_FOUND);
	}

	@GetMapping(value = "js/index.js.map", produces = "text/javascript")
	public Mono<ResponseEntity<String>> indexJSMap(
			@RequestHeader(name = "If-None-Match", required = false) String eTag) {

		return Mono.just(ResponseEntity.notFound()
				.build());
	}

	@GetMapping(value = "manifest/manifest.json", produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
	public Mono<ResponseEntity<String>> manifest(@RequestHeader("appCode") String appCode,
			@RequestHeader("clientCode") String clientCode,
			@RequestHeader(name = "If-None-Match", required = false) String eTag) {

		return manifestService.getManifest(appCode, clientCode)
				.flatMap(e -> ResponseEntityUtils.makeResponseEntity(e, eTag, cacheAge))
				.defaultIfEmpty(RESPONSE_NOT_FOUND);
	}

	@GetMapping(value = "**", produces = MimeTypeUtils.TEXT_HTML_VALUE)
	public Mono<ResponseEntity<String>> defaultMapping(@RequestHeader("appCode") String appCode,
			@RequestHeader("clientCode") String clientCode,
			@RequestHeader(name = "If-None-Match", required = false) String eTag,
			ServerHttpRequest request) {

		return uriService.getResponse(request, null, appCode, clientCode)
				.switchIfEmpty(indexHTMLService.getIndexHTML(appCode, clientCode))
				.flatMap(e -> ResponseEntityUtils.makeResponseEntity(e, eTag, cacheAge))
				.defaultIfEmpty(RESPONSE_NOT_FOUND);
	}

	@PostMapping(value = "**", produces = MimeTypeUtils.TEXT_HTML_VALUE)
	public Mono<ResponseEntity<String>> defaultPostMapping(@RequestHeader("appCode") String appCode,
			@RequestHeader("clientCode") String clientCode,
			@RequestHeader(name = "If-None-Match", required = false) String eTag,
			ServerHttpRequest request,
			@RequestBody String jsonString) {

		JsonObject jsonObject = StringUtil.safeIsBlank(jsonString) ? new JsonObject()
				: this.gson.fromJson(jsonString, JsonObject.class);

		return uriService.getResponse(request, jsonObject, appCode, clientCode)
				.switchIfEmpty(indexHTMLService.getIndexHTML(appCode, clientCode))
				.flatMap(e -> ResponseEntityUtils.makeResponseEntity(e, eTag, cacheAge))
				.defaultIfEmpty(RESPONSE_NOT_FOUND);
	}

	@PutMapping(value = "**", produces = MimeTypeUtils.TEXT_HTML_VALUE)
	public Mono<ResponseEntity<String>> defaultPutMapping(@RequestHeader("appCode") String appCode,
			@RequestHeader("clientCode") String clientCode,
			@RequestHeader(name = "If-None-Match", required = false) String eTag,
			ServerHttpRequest request,
			@RequestBody String jsonString) {

		JsonObject jsonObject = StringUtil.safeIsBlank(jsonString) ? new JsonObject()
				: this.gson.fromJson(jsonString, JsonObject.class);

		return uriService.getResponse(request, jsonObject, appCode, clientCode)
				.switchIfEmpty(indexHTMLService.getIndexHTML(appCode, clientCode))
				.flatMap(e -> ResponseEntityUtils.makeResponseEntity(e, eTag, cacheAge))
				.defaultIfEmpty(RESPONSE_NOT_FOUND);
	}

	@GetMapping("/.well-known/acme-challenge/{token}")
	public Mono<ResponseEntity<String>> tokenCheck(@PathVariable String token) {

		return this.securityService.token(token).map(ResponseEntity::ok);
	}
}
