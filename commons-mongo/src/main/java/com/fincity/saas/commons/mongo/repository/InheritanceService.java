package com.fincity.saas.commons.mongo.repository;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.service.CacheService;

import reactor.core.publisher.Mono;

@Service
public class InheritanceService {

	private static final String CACHE_NAME_INHERITANCE_ORDER = "inheritanceOrder";

	@Autowired
	private IFeignSecurityService securityService;

	@Autowired
	private CacheService cacheService;

	public Mono<List<String>> order(String appCode, String urlClientCode, String clientCode) {

		return cacheService.cacheValueOrGet(CACHE_NAME_INHERITANCE_ORDER,
		        () -> this.securityService.appInheritance(appCode, urlClientCode, clientCode), appCode, ":",
		        urlClientCode, ":", clientCode);
	}
}
