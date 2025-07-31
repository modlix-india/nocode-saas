package com.fincity.saas.commons.security.util;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;

import com.fincity.nocode.kirun.engine.runtime.expression.ExpressionEvaluator;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.util.StringUtil;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public class SecurityContextUtil {

    public static final Logger logger = LoggerFactory.getLogger(SecurityContextUtil.class);

    public static Mono<BigInteger> getUsersClientId() {

        return getUsersContextUser().map(ContextUser::getClientId);
    }

    public static Mono<Locale> getUsersLocale() {

        return getUsersContextUser()
                .flatMap(e -> Mono.justOrEmpty(e.getLocaleCode()))
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

    public static Mono<Tuple2<String, String>> resolveAppAndClientCode(String appCode, String clientCode) {

        if (!StringUtil.safeIsBlank(appCode) && !StringUtil.safeIsBlank(clientCode)) {
            return Mono.just(Tuples.of(appCode, clientCode));
        }

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .cast(ContextAuthentication.class)
                .map(auth -> Tuples.of(
                        !StringUtil.safeIsBlank(appCode) ? appCode : auth.getUrlAppCode(),
                        !StringUtil.safeIsBlank(clientCode) ? clientCode : auth.getClientCode()
                ));
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

}
