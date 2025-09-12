package com.modlix.saas.commons2.controller;

import java.util.Collection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.modlix.saas.commons2.service.CacheService;

public class AbstractCacheController {

	@Autowired
	protected CacheService service;

	@DeleteMapping("internal/cache/{cacheName}")
	public ResponseEntity<Boolean> resetCache(@PathVariable("cacheName") String cacheName) {
		return ResponseEntity.ok(this.service.evictAll(cacheName));
	}

	@DeleteMapping("internal/cache")
	public ResponseEntity<Boolean> resetCache() {
		return ResponseEntity.ok(this.service.evictAllCaches());
	}

	@GetMapping("internal/cache")
	public ResponseEntity<Collection<String>> getCacheNames() {
		return ResponseEntity.ok(this.service.getCacheNames());
	}
}
