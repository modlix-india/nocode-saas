package com.fincity.security.model.billing;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.jooq.types.ULong;

/**
 * A single metered-action charge posted by an owning service. Carries only the
 * raw usage count; security resolves config(C, app) and does all pricing.
 */
public record ChargeRequest(
        ULong configClientId,
        ULong billedClientId,
        ULong appId,
        String actionKey,
        BigDecimal quantity,
        LocalDate date,
        Integer windowIndex) {
}
