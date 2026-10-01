package com.fincity.saas.message.model.response.call;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

/** Whether an agent can take calls in the browser, and under which provider. */
@Data
@Accessors(chain = true)
public class BrowserCallStatus implements Serializable {

    @Serial
    private static final long serialVersionUID = 3390274118845206617L;

    /**
     * Whether this service holds a browser endpoint for the agent. Says the agent was provisioned, not that a call
     * will succeed; {@code dialReadyChecked} says whether the provider confirmed it on this request.
     */
    private boolean provisioned;

    private String provider;
    private String providerUserId;
    private String virtualNumber;

    /** The connection this answer is for: the one the page named, or the agent's own. */
    private String connectionName;

    /** Where the softphone loads the provider's calling library from; overrides the page's setting when set. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String sdkUrl;

    /**
     * Whether the provider was asked, on this request, whether the agent can dial; then {@code provisioned} is the
     * provider's view. A SIP client can register for an agent who cannot originate, so only this proves dialling.
     */
    private boolean dialReadyChecked;

    public static BrowserCallStatus notProvisioned(String provider) {
        return new BrowserCallStatus().setProvisioned(false).setProvider(provider);
    }

    public BrowserCallStatus checkedWithProvider() {
        return this.setDialReadyChecked(true);
    }

    public static BrowserCallStatus of(String provider, String providerUserId, String virtualNumber) {
        return new BrowserCallStatus()
                .setProvisioned(true)
                .setProvider(provider)
                .setProviderUserId(providerUserId)
                .setVirtualNumber(virtualNumber);
    }
}
