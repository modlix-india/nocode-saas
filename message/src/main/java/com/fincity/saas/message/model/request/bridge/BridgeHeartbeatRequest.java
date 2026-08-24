package com.fincity.saas.message.model.request.bridge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * The periodic liveness and state report, every fifteen seconds.
 *
 * <p>Distinct from a Prometheus scrape and not a substitute for one. A scrape says the process
 * answers; this says the process believes it is healthy and reports what it is holding. A wedged
 * bridge still serves {@code /metrics}, so a stale heartbeat with {@code up == 1} is a real and
 * separate alert.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BridgeHeartbeatRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 2298817504493196625L;

    private String instanceId;

    /** Excludes terminal sessions, because that is what the cap is measured against. */
    private Integer activeSessions;

    /** Everything held, so the dead weight waiting on the reaper is visible rather than inferred. */
    private Integer heldSessions;

    private List<BridgeSessionSnapshot> sessions;

    /**
     * Slots the instance has reclaimed from numbers that are not coming back.
     *
     * <p>Repeated on every heartbeat until acknowledged, because a retirement this service never
     * hears about leaves the assignment pointing at a session that no longer exists, and the
     * customer pinned to an instance that has already discarded them.
     */
    private List<BridgeRetiredSession> retired;
}
