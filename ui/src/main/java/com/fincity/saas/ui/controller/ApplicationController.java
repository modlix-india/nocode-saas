package com.fincity.saas.ui.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.ui.document.Application;
import com.fincity.saas.ui.repository.ApplicationRepository;
import com.fincity.saas.ui.service.ApplicationService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/ui/applications")
public class ApplicationController
        extends AbstractUIController<Application, ApplicationRepository, ApplicationService> {

	@GetMapping("/internal/getAppNClientCode")
	public Mono<ResponseEntity<List<String>>> getAppNClientCode(@RequestParam String scheme, @RequestParam String host,
	        @RequestParam String port) {
		return this.service.getAppNClientCodes(scheme, host, port)
		        .map(ResponseEntity::ok);
	}
}
