package com.fincity.saas.ui.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.ui.document.Application;
import com.fincity.saas.ui.document.Function;
import com.fincity.saas.ui.document.Page;
import com.fincity.saas.ui.service.ApplicationService;
import com.fincity.saas.ui.service.FunctionService;
import com.fincity.saas.ui.service.PageService;
import com.fincity.saas.ui.service.ThemeService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/ui/")
public class EngineController {

	@Autowired
	private ApplicationService appService;

	@Autowired
	private PageService pageService;

	@Autowired
	private ThemeService themeService;

	@Autowired
	private FunctionService functionService;

	@GetMapping("application")
	public Mono<ResponseEntity<Application>> application(@RequestHeader("appCode") String appCode,
	        @RequestHeader("clientCode") String clientCode) {

		return this.appService.read(appCode, appCode, clientCode)
		        .map(ResponseEntity::ok);
	}

	@GetMapping("page/{pageName}")
	public Mono<ResponseEntity<Page>> page(@RequestHeader("appCode") String appCode,
	        @RequestHeader("clientCode") String clientCode, @PathVariable("pageName") String pageName) {

		return this.pageService.read(pageName, appCode, clientCode)
		        .map(ResponseEntity::ok);
	}

	@GetMapping(value = "theme/{themeName}", produces = { "text/css" })
	public Mono<ResponseEntity<String>> theme(@RequestHeader("appCode") String appCode,
	        @RequestHeader("clientCode") String clientCode, @PathVariable("themeName") String themeName) {

		return this.themeService.readCSS(themeName, appCode, clientCode)
		        .map(ResponseEntity::ok);
	}

	@GetMapping("function/{functionName}")
	public Mono<ResponseEntity<Function>> function(@RequestHeader("appCode") String appCode,
	        @RequestHeader("clientCode") String clientCode, @PathVariable("functionName") String pageName) {

		return this.functionService.read(pageName, appCode, clientCode)
		        .map(ResponseEntity::ok);
	}
}
