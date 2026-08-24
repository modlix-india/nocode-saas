package com.fincity.saas.message.enums.bridge;

/**
 * Whether an instance may be given new sessions.
 *
 * <p>Only {@link #UP} takes placements. The other two exist because "cannot take new work" and
 * "cannot do its existing work" are different situations that want different responses, and
 * collapsing them into one flag is how a routine deploy starts looking like an outage.
 */
public enum BridgeInstanceState {

    /** Heartbeating and under its cap. Eligible for placement. */
    UP,

    /**
     * Taken out of placement on purpose, ahead of a deploy or a decommission.
     *
     * <p>Its sessions keep working and keep being routed to; only new ones stop arriving. This is
     * what makes a rolling deploy drain rather than parallel-run, which matters more here than
     * anywhere else in the fleet: two processes on one device store corrupt the Signal ratchet, and
     * that is not recoverable without every customer on the instance re-scanning a QR code.
     */
    DRAINING,

    /**
     * Missed three consecutive heartbeats.
     *
     * <p>Its sessions are down and stay assigned to it. They are deliberately <b>not</b> failed over,
     * because a session has exactly one home and a second home is the unrecoverable case above.
     */
    DOWN
}
