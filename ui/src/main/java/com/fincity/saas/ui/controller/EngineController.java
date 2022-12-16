package com.fincity.saas.ui.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.mongo.document.Function;
import com.fincity.saas.commons.mongo.service.FunctionService;
import com.fincity.saas.ui.document.Application;
import com.fincity.saas.ui.document.Page;
import com.fincity.saas.ui.document.Style;
import com.fincity.saas.ui.service.ApplicationService;
import com.fincity.saas.ui.service.PageService;
import com.fincity.saas.ui.service.StyleService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/ui/")
public class EngineController {

	@Autowired
	private ApplicationService appService;

	@Autowired
	private PageService pageService;

	@Autowired
	private StyleService themeService;

	@Autowired
	private FunctionService functionService;

	@GetMapping("application")
	public Mono<ResponseEntity<Application>> application(@RequestHeader("appCode") String appCode,
	        @RequestHeader("clientCode") String clientCode) {

		return this.appService.read(appCode, appCode, clientCode)
		        .map(ResponseEntity::ok)
		        .switchIfEmpty(Mono.defer(() -> Mono.just(ResponseEntity.notFound()
		                .build())));
	}

	@GetMapping("page/{pageName}")
	public Mono<ResponseEntity<Page>> page(@RequestHeader("appCode") String appCode,
	        @RequestHeader("clientCode") String clientCode, @PathVariable("pageName") String pageName) {

		return this.pageService.read(pageName, appCode, clientCode)
		        .map(ResponseEntity::ok)
		        .switchIfEmpty(Mono.defer(() -> Mono.just(ResponseEntity.notFound()
		                .build())));
	}

	@GetMapping(value = "style/{styleName}", produces = { "text/css" })
	public Mono<ResponseEntity<String>> theme(@RequestHeader("appCode") String appCode,
	        @RequestHeader("clientCode") String clientCode, @PathVariable("styleName") String themeName) {

		return this.themeService.read(themeName, appCode, clientCode)
				.map(Style::getStyleString)
		        .map(ResponseEntity::ok)
		        .switchIfEmpty(Mono.defer(() -> Mono.just(ResponseEntity.notFound()
		                .build())));
	}

	@GetMapping("function/{functionName}")
	public Mono<ResponseEntity<Function>> function(@RequestHeader("appCode") String appCode,
	        @RequestHeader("clientCode") String clientCode, @PathVariable("functionName") String pageName) {

		return this.functionService.read(pageName, appCode, clientCode)
		        .map(ResponseEntity::ok)
		        .switchIfEmpty(Mono.defer(() -> Mono.just(ResponseEntity.notFound()
		                .build())));
	}
}
