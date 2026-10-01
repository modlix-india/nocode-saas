package com.fincity.saas.message.dto.call;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.message.dto.base.BaseUpdatableDto;
import com.fincity.saas.message.util.NameUtil;
import java.io.Serial;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

/**
 * A tenant's integration app with a calling provider. Holds the credentials that mint browser-calling tokens, so
 * it gets no {@code BaseUpdatableController}: the eager read paths bypass {@link JsonIgnore}.
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public class CallProviderApp extends BaseUpdatableDto<CallProviderApp> {

    @Serial
    private static final long serialVersionUID = 3812774556291043318L;

    private String connectionName;
    private String provider;

    private String providerAppId;

    @JsonIgnore
    @ToString.Exclude
    private String providerAppSecret;

    private String providerAppName;
    private String accountSid;

    /** Where the provider posts status for browser-originated calls, which never run the App Bazaar flow. */
    private String callbackUrl;

    @JsonIgnore
    @ToString.Exclude
    private Map<String, Object> providerMetadata;

    public CallProviderApp() {
        super();
    }

    public CallProviderApp(CallProviderApp callProviderApp) {
        super(callProviderApp);
        this.connectionName = callProviderApp.connectionName;
        this.provider = callProviderApp.provider;
        this.providerAppId = callProviderApp.providerAppId;
        this.providerAppSecret = callProviderApp.providerAppSecret;
        this.providerAppName = callProviderApp.providerAppName;
        this.accountSid = callProviderApp.accountSid;
        this.callbackUrl = callProviderApp.callbackUrl;
        this.providerMetadata = callProviderApp.providerMetadata;
    }

    public CallProviderApp setProvider(String provider) {
        this.provider = NameUtil.normalizeToUpper(provider);
        return this;
    }
}
