package com.modlix.saas.adzump.service.apply;

/**
 * The {@link GuardrailEngine}'s decision for one action at apply time (J13 §5.2). A breach is never a
 * silent apply: it is either a {@link Outcome#REJECT} (stale, not applied), a {@link Outcome#QUEUE}
 * (downgraded, surfaced for a human), or a {@link Outcome#SUPPRESS} (logged, not applied); only
 * {@link Outcome#OK} proceeds to the mutation spine.
 *
 * @param outcome the guardrail decision.
 * @param reason  a short machine-readable reason (audited).
 */
public record GuardrailVerdict(Outcome outcome, String reason) {

    public enum Outcome {
        /** Cleared every guardrail — proceed to apply (subject to the kill-switch). */
        OK,
        /** Downgraded to the human queue (a cap/rate/do-no-harm breach that a human may still approve). */
        QUEUE,
        /** Suppressed outright with a logged reason (e.g. a kill on immature signal, only-converter). */
        SUPPRESS,
        /** Rejected: the action is stale (built off a snapshot older than the policy allows). */
        REJECT
    }

    public boolean ok() {
        return this.outcome == Outcome.OK;
    }

    public static GuardrailVerdict cleared() {
        return new GuardrailVerdict(Outcome.OK, "guardrails_cleared");
    }

    public static GuardrailVerdict queue(String reason) {
        return new GuardrailVerdict(Outcome.QUEUE, reason);
    }

    public static GuardrailVerdict suppress(String reason) {
        return new GuardrailVerdict(Outcome.SUPPRESS, reason);
    }

    public static GuardrailVerdict reject(String reason) {
        return new GuardrailVerdict(Outcome.REJECT, reason);
    }
}
