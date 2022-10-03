package com.fincity.saas.ui.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.ui.service.PersonalizationService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/ui/personalization")
public class PersonalizationController {

	@Autowired
	private PersonalizationService service;

	@PostMapping("/{appName}/{name}")
	public Mono<ResponseEntity<Map<String, Object>>> create(@PathVariable String appName, @PathVariable String name,
	        @RequestBody Map<String, Object> personalization) {
		return this.service.addPersonalization(appName, name, personalization)
		        .map(ResponseEntity::ok);
	}

	@GetMapping("/{appName}/{name}")
	public Mono<ResponseEntity<Map<String, Object>>> get(@PathVariable String appName, @PathVariable String name) {
		return this.service.getPersonalization(appName, name)
		        .map(ResponseEntity::ok);
	}

	@DeleteMapping("/{appName}/{name}")
	public Mono<ResponseEntity<Boolean>> delete(@PathVariable String appName, @PathVariable String name) {
		return this.service.deletePersonalization(appName, name)
		        .map(ResponseEntity::ok);
	}
}
