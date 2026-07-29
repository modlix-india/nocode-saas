package com.fincity.security.model.billing;

import java.math.BigDecimal;

/**
 * One metered dimension of a client's plan, for the buyer-facing "what am I
 * charged for" view: the per-unit token rate, the free allowance included before
 * charging starts, and the caller's current usage (null when the count lives in
 * another service and is not resolved locally). Carries no gateway secrets or
 * seller details from the underlying config.
 */
public record PlanDimension(
        String actionKey,
        String label,
        String unit,
        BigDecimal tokensPerUnit,
        BigDecimal freeAllowance,
        Integer currentUsage) {
}
