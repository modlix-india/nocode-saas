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

    /**
     * Treat as plaintext.
     *
     * <p>Exotel ships this encrypted, but under a key hardcoded in its own public client SDK, so the
     * ciphertext is no better protected than the value. Never let it reach a read path.
     */
    @JsonProperty("SipSecret")
    @ToString.Exclude
    private String sipSecret;

    @JsonProperty("IsActive")
    private Boolean isActive;
}
