package com.fincity.saas.commons.core.service.security;

import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import java.math.BigInteger;
import java.util.Locale;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ContextService {

    public Mono<BigInteger> getUsersClientId() {
        return SecurityContextUtil.getUsersClientId();
    }

    public Mono<Locale> getUsersLocale() {
        return SecurityContextUtil.getUsersLocale();
    }

    public Mono<ContextAuthentication> getUsersContextAuthentication() {
        return SecurityContextUtil.getUsersContextAuthentication();
    }

    public Mono<ContextUser> getUsersContextUser() {
        return SecurityContextUtil.getUsersContextUser();
    }

    public Mono<Boolean> hasAuthority(String authority) {
        return SecurityContextUtil.hasAuthority(authority);
    }
}
