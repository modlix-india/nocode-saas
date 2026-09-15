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
 * One place a given agent can be reached, and where it sits in the ringing order.
 *
 * <p>An agent normally has two: a {@code WEBRTC_SIP} endpoint at priority 1 and a
 * {@code PSTN_PHONE} endpoint at priority 2. Ringing is sequential, so the priority is what Exotel
 * actually dials in order, not bookkeeping.
 *
 * <p>The agent is the {@code userId} inherited from {@link BaseUpdatableDto}. Do not redeclare it
 * here.
 *
 * <p>Like {@link CallProviderApp}, this entity gets no {@code BaseUpdatableController}: its
 * metadata carries the provider's SIP secret.
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public class ProviderUserEndpoint extends BaseUpdatableDto<ProviderUserEndpoint> {

    @Serial
    private static final long serialVersionUID = 8004266133417790215L;

    private String connectionName;
    private String provider;

    private String endpointType;
    private String endpointValue;

    private Integer priority;

    private String virtualNumber;
    private String providerUserId;

    /**
     * Rest of the provider's response, including the SIP secret.
     *
     * <p>Treat as plaintext regardless of what the provider calls it: Exotel returns the SIP secret
     * encrypted under a key hardcoded in its own public client SDK, so the ciphertext is no more
     * protected than the value. Never expose this through a read path.
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
