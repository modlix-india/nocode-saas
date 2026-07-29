package com.modlix.saas.files.model.billing;

import org.jooq.types.ULong;

/**
 * One billable (C, app, M) row streamed from security for a metered action. Files
 * reports M's raw stored GB; security prices it. Mirror of security's record
 * (cross-service services cannot import security's types).
 */
public record MeteringInstruction(
        String configClientCode,
        ULong configClientId,
        String appCode,
        ULong appId,
        String billedClientCode,
        ULong billedClientId) {
}
