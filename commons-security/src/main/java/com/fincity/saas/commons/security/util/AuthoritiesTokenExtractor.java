package com.fincity.saas.commons.security.util;

import com.fincity.nocode.kirun.engine.runtime.expression.tokenextractor.TokenValueExtractor;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public class AuthoritiesTokenExtractor extends TokenValueExtractor {

    private final Collection<? extends GrantedAuthority> authorities;
    private Set<String> stringAuths;

    public AuthoritiesTokenExtractor(Collection<? extends GrantedAuthority> authorities) {

        this.authorities = authorities;
    }

    @Override
    protected JsonElement getValueInternal(String token) {
        if (stringAuths == null) {
            this.stringAuths = authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        }
        return new JsonPrimitive(this.stringAuths.contains(token.trim()));
    }

    @Override
    public String getPrefix() {
        return "Authorities.";
    }

    @Override
    public JsonElement getStore() {

        JsonArray arr = new JsonArray();
        if (stringAuths == null) return arr;

        authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .forEach(arr::add);
        return arr;
    }
}
