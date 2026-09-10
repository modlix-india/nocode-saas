package com.fincity.saas.message.model.response.call;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

/**
 * One provisioned agent, as an admin screen wants to see them: one row per person.
 *
 * <p>The table underneath holds one row per <em>destination</em> — a {@code WEBRTC_SIP} row and a
 * {@code PSTN_PHONE} row for the same agent — because ringing is sequential and the rows carry the
 * order the provider dials. That shape is right for routing and wrong for a listing, where it shows
 * the same person twice and invites an operator to deactivate "the other one".
 *
 * <p>Deliberately carries no provider metadata. The endpoint rows hold the agent's SIP secret in
 * {@code providerMetadata}; naming the fields wanted here rather than returning the rows means that
 * secret has no path to a response at all.
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

    /**
     * Whether any of this agent's destinations is still live.
     *
     * <p>Any, not all: deactivation clears every row for the agent together, so a mix means
     * something partial happened and the honest answer for a screen is that they are still
     * reachable. An operator seeing "active" with one destination missing has something to look at,
     * where "inactive" on a reachable agent would be a lie.
     */
    private boolean active;

    /** When this agent's provisioning last changed, taken as the latest across their rows. */
    private LocalDateTime updatedAt;
}
