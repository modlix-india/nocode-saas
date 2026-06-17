package com.fincity.security.model.billing;

import java.math.BigDecimal;

import org.jooq.types.ULong;

/**
 * Result of a wallet reservation (pre-authorization for an expensive action).
 * The reservation id is the ledger RESERVE entry id; settle/release reference it.
 */
public record ReservationResult(
        boolean allowed,
        ChargeOutcome outcome,
        ULong reservationId,
        BigDecimal creditsReserved,
        BigDecimal balanceAfter) {

    public static ReservationResult reserved(ULong reservationId, BigDecimal credits, BigDecimal balanceAfter) {
        return new ReservationResult(true, ChargeOutcome.CHARGED, reservationId, credits, balanceAfter);
    }

    public static ReservationResult blocked(ChargeOutcome outcome, BigDecimal balanceAfter) {
        return new ReservationResult(false, outcome, null, BigDecimal.ZERO, balanceAfter);
    }
}
