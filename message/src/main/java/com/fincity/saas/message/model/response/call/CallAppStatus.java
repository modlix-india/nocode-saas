package com.fincity.saas.message.model.response.call;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Whether this tenant's calling app exists at the provider, for a settings screen to read.
 *
 * <p>Answered from our own row with no provider round trip, so it is cheap enough for a page load.
 * Separate from {@code BrowserCallStatus}, which answers the different question of whether one
 * <em>agent</em> can take calls: a tenant can have a perfectly good app and no provisioned agents,
 * and a screen needs to tell those apart to know which button to offer.
 *
 * <p><b>A purpose-built response rather than the {@code CallProviderApp} entity.</b> That entity
 * carries the provider app secret and the raw provider payload — both {@code @JsonIgnore}d today,
 * which is one annotation away from not being. It also inherits the audit and identity fields of
 * every row, none of which a status badge wants. Naming the four fields here means a field added to
 * the table cannot start appearing in an API response by accident.
 *
 * <p>Not initialised is a {@code 200} with {@code initialized: false}, not a {@code 204}. A badge
 * needs something to render, and an empty body forces the caller to infer meaning from a status
 * code.
 */
@Data
@Accessors(chain = true)
public class CallAppStatus implements Serializable {

    @Serial
    private static final long serialVersionUID = 6817425610933054142L;

    /** Whether this service holds a provider app registration for the tenant on this connection. */
    private boolean initialized;

    private String provider;

    /** The name the app is registered under, so an operator can find it in the provider's console. */
    private String appName;

    /**
     * The status callback URL registered on the app.
     *
     * <p>Worth showing: it is the one piece of setup that silently stops working when a host
     * changes, and the symptom — calls that complete but report no duration or recording — looks
     * nothing like a wrong URL.
     */
    private String callbackUrl;

    public static CallAppStatus notInitialized(String provider) {
        return new CallAppStatus().setInitialized(false).setProvider(provider);
    }

    public static CallAppStatus of(String provider, String appName, String callbackUrl) {
        return new CallAppStatus()
                .setInitialized(true)
                .setProvider(provider)
                .setAppName(appName)
                .setCallbackUrl(callbackUrl);
    }
}
