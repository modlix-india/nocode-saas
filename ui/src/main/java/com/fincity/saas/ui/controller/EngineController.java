package com.fincity.saas.ui.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.ui.document.Application;
import com.fincity.saas.ui.service.ApplicationService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/ui/")
public class EngineController {

	@Autowired
	private ApplicationService appService;

	@GetMapping("application")
	public Mono<ResponseEntity<Application>> application(@RequestHeader("appCode") String appCode,
	        @RequestHeader("clientCode") String clientCode) {

		return this.appService.read(appCode, appCode, clientCode)
		        .map(ResponseEntity::ok);
	}
}
