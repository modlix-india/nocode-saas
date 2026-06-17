package com.fincity.security.model.billing;

import java.math.BigDecimal;

import org.jooq.types.ULong;

/**
 * Result of a wallet charge. {@code allowed} tells the caller whether the action
 * may proceed; {@code outcome} carries the detail. A BLOCKED_* outcome maps to
 * HTTP 402 at the controller boundary.
 */
public record ChargeResult(
        boolean allowed,
        ChargeOutcome outcome,
        BigDecimal creditsCharged,
        BigDecimal balanceAfter,
        ULong transactionId) {

    public static ChargeResult charged(BigDecimal credits, BigDecimal balanceAfter, ULong txnId) {
        return new ChargeResult(true, ChargeOutcome.CHARGED, credits, balanceAfter, txnId);
    }

    public static ChargeResult grace(BigDecimal credits, BigDecimal balanceAfter, ULong txnId) {
        return new ChargeResult(true, ChargeOutcome.GRACE_ALLOWED, credits, balanceAfter, txnId);
    }

    public static ChargeResult blocked(ChargeOutcome outcome, BigDecimal balanceAfter) {
        return new ChargeResult(false, outcome, BigDecimal.ZERO, balanceAfter, null);
    }

    public static ChargeResult shadow(BigDecimal credits, BigDecimal balanceAfter, ULong txnId) {
        return new ChargeResult(true, ChargeOutcome.SHADOW_RECORDED, credits, balanceAfter, txnId);
    }

    public static ChargeResult replay(BigDecimal credits, BigDecimal balanceAfter, ULong txnId) {
        return new ChargeResult(true, ChargeOutcome.IDEMPOTENT_REPLAY, credits, balanceAfter, txnId);
    }

    /** No (app, urlClient) billing config exists: allowed, nothing charged or recorded. */
    public static ChargeResult notEnforced() {
        return new ChargeResult(true, ChargeOutcome.NOT_ENFORCED, BigDecimal.ZERO, null, null);
    }
}
