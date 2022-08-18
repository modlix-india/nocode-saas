package com.fincity.security.util;

import java.math.BigInteger;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;

import com.fincity.nocode.kirun.engine.runtime.expression.ExpressionEvaluator;
import com.fincity.nocode.kirun.engine.runtime.expression.tokenextractor.TokenValueExtractor;
import com.fincity.security.jwt.ContextAuthentication;
import com.fincity.security.jwt.ContextUser;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

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

		return getUsersContextAuthentication().map(ContextAuthentication::getUser);
	}

	public static Mono<ContextAuthentication> getUsersContextAuthentication() {
		return ReactiveSecurityContextHolder.getContext()
		        .map(SecurityContext::getAuthentication)
		        .map(ContextAuthentication.class::cast);
	}

	public static Mono<Boolean> hasAuthority(String authority) {

		if (authority == null || authority.isBlank())
			return Mono.just(true);

		return getUsersContextUser().map(e -> hasAuthority(authority, e.getAuthorities()));
	}

	public static boolean hasAuthority(String authority, Collection<? extends GrantedAuthority> collection) {

		if (authority == null || authority.isBlank())
			return true;

		if (collection == null || collection.isEmpty())
			return false;

		ExpressionEvaluator ev = new ExpressionEvaluator(authority);
		AuthoritiesTokenExtractor extractor = new AuthoritiesTokenExtractor(collection);
		JsonPrimitive jp = ev.evaluate(Map.of(extractor.getPrefix(), extractor))
		        .getAsJsonPrimitive();
		return jp.isBoolean() && jp.getAsBoolean();
	}

	private SecurityContextUtil() {
	}

	private static class AuthoritiesTokenExtractor extends TokenValueExtractor {

		private Set<? extends GrantedAuthority> authorities;

		public AuthoritiesTokenExtractor(Collection<? extends GrantedAuthority> authorities) {

			if (authorities instanceof Set<? extends GrantedAuthority> setAuth)
				this.authorities = setAuth;
			else
				this.authorities = new HashSet<>(authorities);
		}

		@Override
		protected JsonElement getValueInternal(String token) {
			return new JsonPrimitive(authorities.contains(new SimpleGrantedAuthority(token)));
		}

		@Override
		public String getPrefix() {
			return "Authorities.";
		}
	}
}
