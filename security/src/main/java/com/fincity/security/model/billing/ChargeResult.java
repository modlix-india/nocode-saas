package com.fincity.security.model.billing;

import java.math.BigDecimal;

/**
 * Outcome of a wallet charge: whether tokens were charged, whether the wallet
 * crossed into suspension, whether the low-balance threshold was crossed, and
 * the resulting balance.
 */
public record ChargeResult(
        boolean charged,
        boolean suspended,
        boolean lowBalanceCrossed,
        BigDecimal balanceAfter) {
}
