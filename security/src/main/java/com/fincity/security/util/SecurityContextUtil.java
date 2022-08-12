package com.fincity.security.util;

import java.math.BigInteger;
import java.util.Locale;

import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;

import com.fincity.security.jwt.ContextAuthentication;
import com.fincity.security.jwt.ContextUser;

import reactor.core.publisher.Mono;

public class SecurityContextUtil {

	public static Mono<BigInteger> getUsersClientId() {
		
		return getUsersContextUser().map(ContextUser::getClientId);
	}

	public static Mono<Locale> getUsersLocale() {

		return getUsersContextUser().map(ContextUser::getLocaleCode)
		        .map(Locale::forLanguageTag)
		        .defaultIfEmpty(Locale.ENGLISH);
	}

	public static Mono<ContextUser> getUsersContextUser() {
		
		return getUsersContextAuthentication()
		        .map(ContextAuthentication::getUser);
	}

	public static Mono<ContextAuthentication> getUsersContextAuthentication() {
		return ReactiveSecurityContextHolder.getContext()
		        .map(SecurityContext::getAuthentication)
		        .map(ContextAuthentication.class::cast);
	}

	private SecurityContextUtil() {
	}
}
