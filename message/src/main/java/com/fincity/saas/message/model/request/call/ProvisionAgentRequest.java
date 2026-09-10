package com.fincity.saas.message.model.request.call;

import com.fincity.saas.message.model.base.BaseMessageRequest;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

/**
 * Maps one agent to a browser-reachable endpoint with the calling provider.
 *
 * <p>{@code userId} and {@code connectionName} come from {@link BaseMessageRequest}.
 */
@FieldNameConstants
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class ProvisionAgentRequest extends BaseMessageRequest {

    @Serial
    private static final long serialVersionUID = 5124398844702118731L;

    /** The agent's mobile, E.164. Becomes their PSTN fallback when the browser is not registered. */
    private String agentNumber;

    /**
     * The virtual number this agent answers on.
     *
     * <p>Required, and supplied by the caller because it has no other source: virtual numbers live
     * in entity-processor's ProductComm, one per product and connection, so this service can neither
     * read them nor choose between several. It sets the agent's outbound caller ID and the
     * provider-side PSTN fallback.
     */
    private String virtualNumber;

    /**
     * The provider identity to map this agent onto, when it is not their own email address.
     *
     * <p>Normally absent: the identity is the agent's own email, which keeps one CRM user to one
     * provider user and makes the mapping self-evident. Supplied only where the provider's identity
     * cannot follow ours — a per-user licence that has to be shared with an existing account, for
     * instance, where the provider's SIP device already hangs off a different address.
     *
     * <p>Using it means the provider knows this agent by a name the CRM does not. Two CRM users given
     * the same value would share one SIP endpoint and ring each other's calls, so provisioning
     * refuses that outright rather than leaving it to be discovered on a live call.
     */
    private String appUserId;
}
