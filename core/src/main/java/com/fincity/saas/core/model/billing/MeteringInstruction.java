package com.fincity.saas.core.model.billing;

import org.jooq.types.ULong;

/**
 * One billable (C, app, M) row streamed from security for a metered action. Core
 * reports the raw row count for (M, app); security prices it. Mirror of security's
 * record (cross-service services cannot import security's model.billing types).
 */
public record MeteringInstruction(
        String configClientCode,
        ULong configClientId,
        String appCode,
        ULong appId,
        String billedClientCode,
        ULong billedClientId) {
}
