package com.fincity.saas.message.model.response.call;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Whether an agent can take calls in the browser, and under which provider.
 *
 * <p>Answered from our own rows with no provider round trip, so it is cheap enough for every page
 * load. The UI needs it to tell an unprovisioned agent apart from a broken integration: without it
 * the phone button would appear for every user in the tenant and error for most of them.
 */
@Data
@Accessors(chain = true)
public class BrowserCallStatus implements Serializable {

    @Serial
    private static final long serialVersionUID = 3390274118845206617L;

    /**
     * Whether this service holds a browser endpoint for the agent.
     *
     * <p>Says the agent was provisioned, not that a call will succeed. This is a read of our own
     * rows: the provider can deactivate a user or drop their SIP device afterwards, and neither
     * shows up here. Provisioning verifies dial-readiness at the provider before writing the rows,
     * so a true here means it was real at that moment — {@code dialReadyChecked} says whether it
     * has been confirmed since.
     */
    private boolean provisioned;

    private String provider;
    private String providerUserId;
    private String virtualNumber;

    /**
     * Whether the provider was asked, on this request, whether the agent can actually dial.
     *
     * <p>False on the cheap path, which is the default because this endpoint runs on every page
     * load. When true, {@code provisioned} reflects the provider's own view rather than ours.
     *
     * <p>The distinction is load-bearing. A SIP client can register successfully against an agent
     * that cannot originate a single call — registration and origination read different records at
     * the provider — so neither our row nor a connected softphone is evidence that dialling works.
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
