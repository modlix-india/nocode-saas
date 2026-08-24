package com.fincity.saas.message.service.bridge;

import java.io.Serial;

/**
 * A bridge heartbeat arrived for an instance this service has no record of.
 *
 * <p>Its own type rather than a generic error, because the response code is the whole point. The
 * bridge's heartbeat loop re-registers only after consecutive failures, so this has to surface as a
 * non-2xx or the instance never re-registers. Answering OK leaves it heartbeating into a void: it
 * looks healthy in its own logs, while placement cannot see it and every session create for its
 * country fails with "no instance available".
 *
 * <p>Not an error worth a stack trace or an alert on its own. It is the expected first thing that
 * happens after this service restarts, and the fleet recovering from it is the design working.
 */
public class BridgeNotRegisteredException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 4986178432610558201L;

    private final String instanceId;

    public BridgeNotRegisteredException(String instanceId) {
        super("Bridge instance '" + instanceId + "' is not registered.");
        this.instanceId = instanceId;
    }

    public String getInstanceId() {
        return this.instanceId;
    }
}
