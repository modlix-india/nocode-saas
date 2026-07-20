package com.modlix.saas.files.model.billing;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.jooq.types.ULong;

/**
 * A single metered-action charge posted to security. Carries only the raw usage
 * count (GB for files); security resolves config(C, app) and does all pricing.
 * Mirror of security's record (cross-service services cannot import security's types).
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
