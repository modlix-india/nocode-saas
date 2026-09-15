package com.fincity.saas.message.model.response.call;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * A short-lived credential for one agent's browser softphone.
 *
 * <p>Provider-neutral on purpose: the UI picks its client adapter from {@code provider} rather than
 * from a page-authored setting, so adding a second provider needs no page edits anywhere.
 *
 * <p>Never cached and never shared between agents. One cached token handed to two agents is a
 * cross-agent credential leak.
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

    public static BrowserCallToken of(String token, String providerUserId, Long expiresIn, String provider) {
        return new BrowserCallToken()
                .setToken(token)
                .setProviderUserId(providerUserId)
                .setExpiresIn(expiresIn)
                .setProvider(provider);
    }
}
