package com.fincity.saas.ui.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.controller.AbstractCacheController;
import com.fincity.saas.ui.service.JSService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/ui/")
public class UICacheController extends AbstractCacheController {

	@DeleteMapping("/cache/jsCache")
	public Mono<ResponseEntity<Integer>> deleteJSCache() {
		return this.service.evictAll(JSService.CACHE_NAME_JS)
		        .map(e -> 1)
		        .map(ResponseEntity::ok);
	}
}
