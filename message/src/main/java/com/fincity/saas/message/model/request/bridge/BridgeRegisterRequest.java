package com.fincity.saas.message.model.request.bridge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * A bridge instance announcing itself to the fleet.
 *
 * <p>Sent at startup and again whenever heartbeats have been failing, since a control plane that
 * restarted will not know the instance exists, and an instance it does not know about receives no
 * placements and no deployments while looking perfectly healthy from the outside.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BridgeRegisterRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 5527041869733521004L;

    private String instanceId;

    /** Where to call this instance, as the instance itself reports it. */
    private String baseUrl;

    /**
     * Countries this instance may host numbers for.
     *
     * <p>Declared by the instance rather than configured here, which is what makes bringing up a
     * country a matter of deploying a box that says which country it is. If adding a country ever
     * requires editing something in Ashburn, the abstraction has gone wrong.
     */
    private List<String> countries;

    private Integer sessionCap;
    private String version;

    /**
     * Every session the instance currently holds, and the whole reason registration carries a body.
     *
     * <p>This is the reconciliation point. Diffed against what this service has assigned, it
     * produces two lists: orphans, assigned here but not present, and strays, present here but
     * assigned elsewhere. The stray is the one that matters, because it means two processes may be
     * holding one device store, and that corrupts the Signal ratchet beyond recovery.
     */
    private List<BridgeSessionSnapshot> heldSessions;
}
