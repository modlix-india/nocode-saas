package com.fincity.security.model.billing;

import org.jooq.types.ULong;

/**
 * One billable (C, app, M) row streamed to a metering service: a config(C, app)
 * carries the action's rate and M is directly managed by C. No rate or free
 * allowance is sent; the service reports the raw count and security prices it.
 */
public record MeteringInstruction(
        String configClientCode,
        ULong configClientId,
        String appCode,
        ULong appId,
        String billedClientCode,
        ULong billedClientId) {
}
