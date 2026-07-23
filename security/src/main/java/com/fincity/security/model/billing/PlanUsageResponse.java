package com.fincity.security.model.billing;

import java.math.BigDecimal;
import java.util.List;

/**
 * The pricing + free-allowance + usage projection shown to a wallet owner, so
 * they can see how tokens are consumed (free tier, per-action rates, current
 * usage) before buying. Deliberately excludes the gateway credentials, rates
 * blob internals and seller details that live on the billing config. Empty
 * dimensions when no config governs the caller.
 */
public record PlanUsageResponse(
        BigDecimal lowBalanceThreshold,
        List<PlanDimension> dimensions) {
}
