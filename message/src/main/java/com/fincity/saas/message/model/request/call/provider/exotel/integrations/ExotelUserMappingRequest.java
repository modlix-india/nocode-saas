package com.fincity.saas.message.model.request.call.provider.exotel.integrations;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Maps one agent onto a SIP device on the tenant's Exotel account. Sent with the app token.
 *
 * <p>Posted inside a JSON array even for a single agent, and answered with an array.
 */
@Data
@Accessors(chain = true)
public class ExotelUserMappingRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 6602281439185027745L;

    /** The agent's identity everywhere downstream. Exotel expects an email. */
    @JsonProperty("AppUserId")
    private String appUserId;

    @JsonProperty("AppUsername")
    private String appUsername;

    @JsonProperty("Email")
    private String email;

    @JsonProperty("ExotelAccountSid")
    private String exotelAccountSid;

    @JsonProperty("ExotelUserName")
    private String exotelUserName;

    /** E.164. The PSTN fallback when the agent's browser is not registered. */
    @JsonProperty("AgentNumber")
    private String agentNumber;

    /** Sets the agent's outbound caller ID and the provider-side PSTN fallback. */
    @JsonProperty("VirtualNumber")
    private String virtualNumber;
}
