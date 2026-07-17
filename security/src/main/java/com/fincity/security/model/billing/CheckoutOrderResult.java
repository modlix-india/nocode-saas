package com.fincity.security.model.billing;

import java.math.BigDecimal;

import org.jooq.types.ULong;

/**
 * The result of starting a token-bundle purchase via the Razorpay Checkout.js
 * (Orders API) flow. Carries everything the browser needs to open the in-page
 * Razorpay modal: the {@code orderId}, the publishable {@code keyId} (never the
 * secret), the {@code amount} in paise (what Checkout.js expects) and prefill
 * fields. The PENDING invoice is echoed for display; the webhook (not this flow)
 * credits the wallet.
 */
public record CheckoutOrderResult(
        String orderId,
        String keyId,
        long amount,
        String currency,
        ULong invoiceId,
        String invoiceNumber,
        BigDecimal totalAmount,
        String prefillName,
        String prefillEmail,
        String prefillContact) {
}
