package com.fincity.saas.ui.controller;

import javax.ws.rs.Path;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.ui.model.ChecksumObject;
import com.fincity.saas.ui.service.IndexHTMLService;
import com.fincity.saas.ui.service.JSService;
import com.fincity.saas.ui.service.ManifestService;

import reactor.core.publisher.Mono;

@RestController
public class UniversalController {

	@Autowired
	private JSService jsService;

	@Autowired
	private IndexHTMLService indexHTMLService;

	@Autowired
	private ManifestService manifestService;

	@Autowired
	private IFeignSecurityService securityService;

	@Value("${ui.jsURL:}")
	private String jsURL;

	@Value("${ui.resourceCacheAge:604800}")
	private int cacheAge;

	@GetMapping(value = "js/index.js", produces = "text/javascript")
	public Mono<ResponseEntity<String>> indexJS(@RequestHeader(name = "If-None-Match", required = false) String eTag) {

		return jsService.getJSObject()
				.map(e -> checkSumObjectToResponseEntity(eTag, e))
				.switchIfEmpty(Mono.defer(() -> Mono.just(ResponseEntity.notFound()
						.build())));
	}

	@GetMapping(value = "js/index.js.map", produces = "text/javascript")
	public Mono<ResponseEntity<String>> indexJSMap(
			@RequestHeader(name = "If-None-Match", required = false) String eTag) {

		return jsService.getJSMapObject()
				.map(e -> checkSumObjectToResponseEntity(eTag, e))
				.switchIfEmpty(Mono.defer(() -> Mono.just(ResponseEntity.notFound()
						.build())));
	}

	@GetMapping(value = "manifest/manifest.json", produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
	public Mono<ResponseEntity<String>> manifest(@RequestHeader("appCode") String appCode,
			@RequestHeader("clientCode") String clientCode,
			@RequestHeader(name = "If-None-Match", required = false) String eTag) {

		return manifestService.getManifest(appCode, clientCode)
				.map(e -> checkSumObjectToResponseEntity(eTag, e))
				.switchIfEmpty(Mono.defer(() -> Mono.just(ResponseEntity.notFound()
						.build())));
	}

	@GetMapping(value = "**", produces = MimeTypeUtils.TEXT_HTML_VALUE)
	public Mono<ResponseEntity<String>> defaultMapping(@RequestHeader("appCode") String appCode,
			@RequestHeader("clientCode") String clientCode,
			@RequestHeader(name = "If-None-Match", required = false) String eTag) {

		return indexHTMLService.getIndexHTML(appCode, clientCode)
				.map(e -> checkSumObjectToResponseEntity(eTag, e))
				.switchIfEmpty(Mono.defer(() -> Mono.just(ResponseEntity.notFound()
						.build())));
	}

	private ResponseEntity<String> checkSumObjectToResponseEntity(String eTag, ChecksumObject e) {

		if (e.getCheckSum()
				.equals(eTag))
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
					.build();

		var rp = ResponseEntity.ok()
				.header("eTag", e.getCheckSum())
				.header("Cache-Control", "max-age: " + cacheAge + ", must-revalidate")
				.header("x-frame-options", "SAMEORIGIN")
				.header("X-Frame-Options", "SAMEORIGIN");

		if (e.getHeaders() != null)
			e.getHeaders()
					.entrySet()
					.stream()
					.forEach(x -> rp.header(x.getKey(), x.getValue()));

		return rp.body(e.getObjectString());
	}

	@GetMapping("/.well-known/acme-challenge/{token}")
	public Mono<ResponseEntity<String>> tokenCheck(@PathVariable String token) {

		return this.securityService.token(token).map(ResponseEntity::ok);
	}
}
