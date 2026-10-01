package com.fincity.saas.message.model.response.call.provider.exotel.integrations;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

/** One agent's SIP device on the tenant's Exotel account. */
@Data
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExotelUserMappingData implements Serializable {

    @Serial
    private static final long serialVersionUID = 8867031425590284116L;

    @JsonProperty("CustomerId")
    private String customerId;

    @JsonProperty("AppID")
    private String appId;

    @JsonProperty("AppUserId")
    private String appUserId;

    @JsonProperty("ExotelAccountSid")
    private String exotelAccountSid;

    @JsonProperty("VirtualNumber")
    private String virtualNumber;

    @JsonProperty("Role")
    private String role;

    /** Arrives already prefixed, e.g. {@code sip:<sipId>}. Do not prepend "sip:" again. */
    @JsonProperty("SipId")
    private String sipId;

    /** Treat as plaintext: Exotel encrypts it under a key hardcoded in its public SDK. Never expose it on reads. */
    @JsonProperty("SipSecret")
    @ToString.Exclude
    private String sipSecret;

    @JsonProperty("IsActive")
    private Boolean isActive;
}
