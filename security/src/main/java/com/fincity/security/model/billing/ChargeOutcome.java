package com.fincity.security.model.billing;

/**
 * Outcome of a wallet charge / reserve attempt.
 */
public enum ChargeOutcome {
    /** Tokens were debited from the available balance. */
    CHARGED,
    /** ENGAGEMENT action allowed despite insufficient balance (within grace floor). */
    GRACE_ALLOWED,
    /** METERED action blocked because the balance is insufficient. */
    BLOCKED_INSUFFICIENT,
    /** Action blocked because a per-app budget cap was reached. */
    BLOCKED_BUDGET_CAP,
    /** A prior transaction with the same idempotency key already applied this charge. */
    IDEMPOTENT_REPLAY,
    /** Shadow mode: would-be debit recorded, balance unchanged. */
    SHADOW_RECORDED,
    /** No billing config exists for (app, urlClient): no enforcement, allowed, nothing recorded. */
    NOT_ENFORCED
}
