package com.fincity.saas.ui.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

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

	@Value("${ui.jsURL:}")
	private String jsURL;

	@GetMapping(value = "js/index.js", produces = "text/javascript")
	public Mono<ResponseEntity<String>> indexJS(@RequestHeader(name = "If-None-Match", required = false) String eTag) {

		return jsService.getJSObject()
		        .map(e -> checkSumObjectToResponseEntity(eTag, e));
	}

	@GetMapping(value = "manifest/manifest.json", produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
	public Mono<ResponseEntity<String>> manifest(@RequestHeader("appCode") String appCode,
	        @RequestHeader("clientCode") String clientCode,
	        @RequestHeader(name = "If-None-Match", required = false) String eTag) {

		return manifestService.getManifest(appCode, clientCode)
		        .map(e -> checkSumObjectToResponseEntity(eTag, e));
	}

	@GetMapping(value = "**", produces = MimeTypeUtils.TEXT_HTML_VALUE)
	public Mono<ResponseEntity<String>> defaultMapping(@RequestHeader("appCode") String appCode,
	        @RequestHeader("clientCode") String clientCode,
	        @RequestHeader(name = "If-None-Match", required = false) String eTag) {

		return indexHTMLService.getIndexHTML(appCode, clientCode)
		        .map(e -> checkSumObjectToResponseEntity(eTag, e));
	}

	private ResponseEntity<String> checkSumObjectToResponseEntity(String eTag, ChecksumObject e) {

		if (e.getCheckSum()
		        .equals(eTag))
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
			        .build();

		return ResponseEntity.ok()
		        .header("eTag", e.getCheckSum())
		        .header("Cache-Control", "max-age: 0, must-revalidate")
		        .body(e.getObjectString());
	}
}
