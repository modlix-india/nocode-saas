package com.fincity.saas.ui.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.controller.AbstractCacheController;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.ui.service.IndexHTMLService;
import com.fincity.saas.ui.service.JSService;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("api/ui/")
public class UICacheController extends AbstractCacheController {

	@GetMapping("/newdeployment")
	public Mono<ResponseEntity<Integer>> newDeployment() {

		return FlatMapUtil.flatMapMono(

				() -> SecurityContextUtil.getUsersContextAuthentication()
						.filter(ContextAuthentication::isAuthenticated),

				isAuthenticatedUser -> {

					Mono.zip(this.service.evictAll(JSService.CACHE_NAME_JS),
							this.service.evictAll(JSService.CACHE_NAME_JS_MAP),
							this.service.evictAll(IndexHTMLService.CACHE_NAME_INDEX))
							.subscribeOn(Schedulers.boundedElastic()).subscribe();

					return Mono.just(ResponseEntity.ok(1));
				}).defaultIfEmpty(ResponseEntity.badRequest().build());

	}
}
