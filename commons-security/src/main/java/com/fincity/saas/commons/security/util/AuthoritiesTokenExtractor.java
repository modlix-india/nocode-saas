package com.fincity.saas.commons.security.util;

import com.fincity.nocode.kirun.engine.runtime.expression.tokenextractor.TokenValueExtractor;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.apache.logging.log4j.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public class AuthoritiesTokenExtractor extends TokenValueExtractor {

    private static final Logger log = LoggerFactory.getLogger(AuthoritiesTokenExtractor.class);

    private final Collection<? extends GrantedAuthority> authorities;

    public AuthoritiesTokenExtractor(Collection<? extends GrantedAuthority> authorities) {

        this.authorities = authorities;
    }

    @Override
    protected JsonElement getValueInternal(String token) {
        return new JsonPrimitive(this.authorities.parallelStream().map(GrantedAuthority::getAuthority).anyMatch(e -> e.equals(token)));
    }

    @Override
    public String getPrefix() {
        return "Authorities.";
    }

    @Override
    public JsonElement getStore() {

        JsonArray arr = new JsonArray();

        authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .forEach(arr::add);
        return arr;
    }
}
