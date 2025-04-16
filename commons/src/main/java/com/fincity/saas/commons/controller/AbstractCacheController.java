package com.fincity.saas.commons.controller;

import java.util.Collection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.fincity.saas.commons.service.CacheService;

import reactor.core.publisher.Mono;

public class AbstractCacheController {

	@Autowired
	protected CacheService service;

	@DeleteMapping("internal/cache/{cacheName}")
	public Mono<ResponseEntity<Boolean>> resetCache(@PathVariable("cacheName") String cacheName) {
		return this.service.evictAll(cacheName).map(ResponseEntity::ok);
	}

	@DeleteMapping("internal/cache")
	public Mono<ResponseEntity<Boolean>> resetCache() {
		return this.service.evictAllCaches().map(ResponseEntity::ok);
	}
	
	@GetMapping("internal/cache")
	public Mono<ResponseEntity<Collection<String>>> getCacheNames() {
		return this.service.getCacheNames().map(ResponseEntity::ok);
	}
}
