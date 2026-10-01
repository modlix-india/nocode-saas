package com.fincity.saas.message.model.response.call;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

/**
 * One provisioned agent, collapsed from their per-destination endpoint rows. Carries no provider metadata, so the
 * SIP secret in {@code providerMetadata} has no path to a response.
 */
@Data
@Accessors(chain = true)
public class ProvisionedAgent implements Serializable {

    @Serial
    private static final long serialVersionUID = 2946115302833810244L;

    private ULong userId;

    /** The provider's identity for this agent. For Exotel, their email address. */
    private String providerUserId;

    /** Where the browser registers. Absent means this agent has no softphone, only a phone. */
    private String sipEndpoint;

    /** The PSTN fallback the provider rings after the browser goes unanswered. */
    private String agentNumber;

    /** The number presented to the customer on this agent's outbound calls. */
    private String virtualNumber;

    /** Whether any of this agent's destinations is still live; a mix means a partial change, still reachable. */
    private boolean active;

    /** When this agent's provisioning last changed, taken as the latest across their rows. */
    private LocalDateTime updatedAt;
}
