package com.fincity.security.model.billing;

import java.math.BigDecimal;

import org.jooq.types.ULong;

/**
 * The result of starting a token-bundle purchase: the PENDING invoice and the
 * hosted Razorpay payment URL the browser should open to complete payment.
 */
public record PurchaseResult(
        ULong invoiceId,
        String invoiceNumber,
        BigDecimal totalAmount,
        String currency,
        String paymentUrl) {
}
