package com.fincity.saas.message.model.response.call;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * Whether this tenant's calling app exists at the provider, answered from our own row.
 *
 * <p>A purpose-built response rather than the {@code CallProviderApp} entity, which carries the provider app
 * secret: a field added to that table cannot reach an API response by accident.
 */
@Data
@Accessors(chain = true)
public class CallAppStatus implements Serializable {

    @Serial
    private static final long serialVersionUID = 6817425610933054142L;

    private boolean initialized;

    private String provider;

    /** The name the app is registered under, so an operator can find it in the provider's console. */
    private String appName;

    /** Shown because a stale host breaks it silently: calls complete but report no duration or recording. */
    private String callbackUrl;

    /** TeleCMI only: the inbound HTTP flow's URL, as the connection states it. Omitted when absent. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String httpFlowUrl;

    /**
     * Call credit as last reported at setup. Informational only: TeleCMI's {@code /v2/balance} has read 0 for an
     * account whose dashboard showed funds.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Double balance;

    /** When the provider account's credit lapses, as last reported at setup. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private LocalDateTime expiresAt;

    /** The webhook token in the clear, only on the setup response that generated it; just its hash is stored. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @ToString.Exclude
    private String webhookToken;

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
