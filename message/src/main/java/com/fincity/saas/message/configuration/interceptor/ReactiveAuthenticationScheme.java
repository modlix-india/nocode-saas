package com.fincity.saas.message.configuration.interceptor;

import lombok.Getter;

@Getter
public enum ReactiveAuthenticationScheme {
    
    BASIC("Basic"),
    
    BEARER("Bearer"),
    
    DIGEST("Digest"),
    
    NONE("");
    
    private final String prefix;
    
    ReactiveAuthenticationScheme(String prefix) {
        this.prefix = prefix;
    }

    public String format(String token) {
        if (this == NONE)
            return token;
        return this.prefix + " " + token;
    }
}
