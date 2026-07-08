package com.modlix.saas.adzump.service.apply;

/**
 * The {@link AutonomyRouter}'s per-action decision (J13 §5.1): may this action be applied now, must it
 * queue for a human, or is it suppressed outright. Routing is decided on <b>mode × risk × declared
 * caps</b> only; the live-state re-check (do-no-harm, live budget, staleness, rate) happens later in the
 * {@link GuardrailEngine} at apply time and can still downgrade an {@link #APPLY} decision.
 */
public enum ApplyRoute {

    /** Eligible to apply now (still subject to the apply-time {@link GuardrailEngine}). */
    APPLY,

    /** Held for a human to approve (recommend-mode, or a change the mode does not auto-apply). */
    QUEUE,

    /** Not actionable on the apply path (e.g. {@code REQUEST_VARIANT} routed out to experiments). */
    SUPPRESS
}
