package com.fincity.security.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.security.service.LimitService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/security/limits")
public class LimitController {

	@Autowired
	private LimitService limitService;

	@GetMapping("/internal/fetchLimits")
	public Mono<ResponseEntity<Long>> fetchLimits(@RequestParam(required = true) String objectName) {

		return this.limitService.fetchLimits(objectName)
				.map(ResponseEntity::ok);
	}

}
