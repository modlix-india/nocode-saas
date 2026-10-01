package com.fincity.saas.message.model.request.call;

import com.fincity.saas.message.model.base.BaseMessageRequest;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

/** Maps one agent to a browser-reachable endpoint; {@code userId} and {@code connectionName} are inherited. */
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
     * The virtual number this agent answers on and calls out from. Required: virtual numbers live in
     * entity-processor's ProductComm, which this service cannot read.
     */
    private String virtualNumber;

    /**
     * The provider identity to map this agent onto when it is not their own email. Two CRM users given the same
     * value would share one SIP endpoint, so provisioning refuses that.
     */
    private String appUserId;

    /**
     * The softphone's login password, TeleCMI only. Required for a new or adopted TeleCMI user; on re-provision,
     * absent keeps and copies TeleCMI's current password. Never logged or returned.
     */
    @ToString.Exclude
    private String password;
}
