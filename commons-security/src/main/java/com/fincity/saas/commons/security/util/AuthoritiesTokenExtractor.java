package com.fincity.saas.commons.security.util;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;

import com.fincity.nocode.kirun.engine.runtime.expression.tokenextractor.TokenValueExtractor;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class AuthoritiesTokenExtractor extends TokenValueExtractor {

    private final List<String> authorities;

    public AuthoritiesTokenExtractor(Collection<? extends GrantedAuthority> authorities) {

        this.authorities = authorities.stream().map(GrantedAuthority::getAuthority).toList();
    }

    public AuthoritiesTokenExtractor(List<String> authorities) {

        this.authorities = authorities;
    }

    @Override
    protected JsonElement getValueInternal(String token) {
        return new JsonPrimitive(authorities.stream().anyMatch(e -> e.equals(token)));
    }

    @Override
    public String getPrefix() {
        return "Authorities.";
    }

    @Override
    public JsonElement getStore() {

        JsonArray arr = new JsonArray();

        authorities.stream().forEach(arr::add);
        return arr;
    }
}
