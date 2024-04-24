package com.fincity.saas.core.service.security.user;

import java.math.BigInteger;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class UserContextService {

	public Mono<BigInteger> getUsersClientId() {
		return SecurityContextUtil.getUsersClientId()
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "UserContextService.getUsersClientId"));
	}

	public Mono<Locale> getUsersLocale() {
		return SecurityContextUtil.getUsersLocale()
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "UserContextService.getUsersLocale"));
	}

	public Mono<ContextAuthentication> getUsersContextAuthentication() {
		return SecurityContextUtil.getUsersContextAuthentication()
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "UserContextService.getUsersContextAuthentication"));
	}

	public Mono<ContextUser> getUsersContextUser() {
		return SecurityContextUtil.getUsersContextUser()
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "UserContextService.getUsersContextUser"));
	}

	public Mono<Boolean> hasAuthority(String authority) {
		return SecurityContextUtil.hasAuthority(authority)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "UserContextService.hasAuthority"));
	}
}
