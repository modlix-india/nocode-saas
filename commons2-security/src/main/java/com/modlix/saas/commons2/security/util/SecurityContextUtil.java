package com.modlix.saas.commons2.security.util;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;

import com.fincity.nocode.kirun.engine.runtime.expression.ExpressionEvaluator;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.jwt.ContextUser;
import com.modlix.saas.commons2.util.StringUtil;
import com.google.gson.JsonPrimitive;

public class SecurityContextUtil {

    public static final Logger logger = LoggerFactory.getLogger(SecurityContextUtil.class);

    public static BigInteger getUsersClientId() {
        ContextUser user = getUsersContextUser();
        return user != null ? user.getClientId() : null;
    }

    public static Locale getUsersLocale() {
        ContextUser user = getUsersContextUser();
        if (user != null && user.getLocaleCode() != null) {
            return Locale.forLanguageTag(user.getLocaleCode());
        }
        return Locale.ENGLISH;
    }

    public static ContextUser getUsersContextUser() {
        ContextAuthentication auth = getUsersContextAuthentication();
        return auth != null ? auth.getUser() : null;
    }

    public static ContextAuthentication getUsersContextAuthentication() {
        SecurityContext context = SecurityContextHolder.getContext();
        if (context != null && context.getAuthentication() instanceof ContextAuthentication) {
            return (ContextAuthentication) context.getAuthentication();
        }
        return null;
    }

    public static String[] resolveAppAndClientCode(String appCode, String clientCode) {
        if (!StringUtil.safeIsBlank(appCode) && !StringUtil.safeIsBlank(clientCode)) {
            return new String[] { appCode, clientCode };
        }

        ContextAuthentication auth = getUsersContextAuthentication();
        if (auth != null) {
            return new String[] {
                    !StringUtil.safeIsBlank(appCode) ? appCode : auth.getUrlAppCode(),
                    !StringUtil.safeIsBlank(clientCode) ? clientCode : auth.getClientCode()
            };
        }
        return new String[] { appCode, clientCode };
    }

    public static boolean hasAuthority(String authority) {
        if (authority == null || authority.isBlank())
            return true;

        ContextUser user = getUsersContextUser();
        return user != null && hasAuthority(authority, user.getAuthorities());
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
