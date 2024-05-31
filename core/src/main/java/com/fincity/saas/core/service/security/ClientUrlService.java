package com.fincity.saas.core.service.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.security.feign.IFeignSecurityService;

import reactor.core.publisher.Mono;

@Service
public class ClientUrlService {

	@Autowired
	private IFeignSecurityService securityService;

	public Mono<String> getAppUrl(String appCode, String clientCode) {
		return securityService.getAppUrl(appCode, clientCode);
	}
}
