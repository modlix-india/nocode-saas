package com.fincity.security.model.billing;

import java.math.BigDecimal;

/**
 * The credit cost and effective class resolved for an action on an app.
 */
public record ResolvedCost(BigDecimal credits, ActionClass actionClass) {
}
