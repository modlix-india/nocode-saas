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
 * One place an agent can be reached; {@code priority} is the order the provider rings them in. The agent is the
 * inherited {@code userId}. No {@code BaseUpdatableController}: its metadata carries the provider's SIP secret.
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public class ProviderUserEndpoint extends BaseUpdatableDto<ProviderUserEndpoint> {

    @Serial
    private static final long serialVersionUID = 8004266133417790215L;

    /** {@code ENDPOINT_TYPE} of an agent's browser softphone. Rings first. */
    public static final String ENDPOINT_WEBRTC_SIP = "WEBRTC_SIP";

    /** {@code ENDPOINT_TYPE} of an agent's own phone, rung when the browser is not reachable. */
    public static final String ENDPOINT_PSTN_PHONE = "PSTN_PHONE";

    private String connectionName;
    private String provider;

    private String endpointType;
    private String endpointValue;

    private Integer priority;

    private String virtualNumber;
    private String providerUserId;

    /**
     * Rest of the provider's response, including the SIP secret. Treat as plaintext: Exotel encrypts it under a key
     * hardcoded in its public SDK. Never expose this through a read path.
     */
    @JsonIgnore
    @ToString.Exclude
    private Map<String, Object> providerMetadata;

    public ProviderUserEndpoint() {
        super();
    }

    public ProviderUserEndpoint(ProviderUserEndpoint providerUserEndpoint) {
        super(providerUserEndpoint);
        this.connectionName = providerUserEndpoint.connectionName;
        this.provider = providerUserEndpoint.provider;
        this.endpointType = providerUserEndpoint.endpointType;
        this.endpointValue = providerUserEndpoint.endpointValue;
        this.priority = providerUserEndpoint.priority;
        this.virtualNumber = providerUserEndpoint.virtualNumber;
        this.providerUserId = providerUserEndpoint.providerUserId;
        this.providerMetadata = providerUserEndpoint.providerMetadata;
    }

    public ProviderUserEndpoint setProvider(String provider) {
        this.provider = NameUtil.normalizeToUpper(provider);
        return this;
    }

    public ProviderUserEndpoint setEndpointType(String endpointType) {
        this.endpointType = NameUtil.normalizeToUpper(endpointType);
        return this;
    }
}
