package com.fincity.saas.message.model.response.call;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * A short-lived credential for one agent's browser softphone; the UI picks its client adapter from
 * {@code provider}. Never cached or shared between agents.
 */
@Data
@Accessors(chain = true)
public class BrowserCallToken implements Serializable {

    @Serial
    private static final long serialVersionUID = 8571936027344881205L;

    @ToString.Exclude
    private String token;

    private String providerUserId;
    private Long expiresIn;
    private String provider;

    /** The provider's signalling region the softphone registers with, for providers that need one. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String region;

    public static BrowserCallToken of(String token, String providerUserId, Long expiresIn, String provider) {
        return new BrowserCallToken()
                .setToken(token)
                .setProviderUserId(providerUserId)
                .setExpiresIn(expiresIn)
                .setProvider(provider);
    }
}
