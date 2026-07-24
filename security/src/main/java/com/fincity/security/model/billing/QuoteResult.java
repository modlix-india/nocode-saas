package com.fincity.security.model.billing;

import java.math.BigDecimal;

import org.jooq.types.ULong;

/**
 * A compute-only price breakup for a bundle purchase, shown in the order-summary
 * popup before the buyer commits to pay. Same math as the invoice
 * (base + config GST), but nothing is persisted and no gateway order is created.
 */
public record QuoteResult(
        ULong bundleId,
        String bundleLabel,
        BigDecimal tokens,
        BigDecimal baseAmount,
        BigDecimal gstPercentage,
        BigDecimal gstAmount,
        BigDecimal totalAmount,
        String currency) {
}
