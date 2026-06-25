package com.fincity.security.model.billing;

import java.math.BigDecimal;

import org.jooq.types.ULong;

/**
 * Start-a-purchase request body. {@code tokens} is required only for a CUSTOM
 * bundle; {@code clientId} is optional (defaults to the caller's client, else a
 * client the caller manages).
 */
public record PurchaseRequest(ULong bundleId, BigDecimal tokens, ULong clientId) {
}
