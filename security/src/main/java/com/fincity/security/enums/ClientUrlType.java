package com.fincity.security.enums;

/**
 * Which surface of an app a hostname serves.
 *
 * LIVE is the published app. DRAFT is the app's draft surface: the gateway
 * resolves a DRAFT hostname and injects the draft marker, which is the only way
 * that marker is ever set.
 *
 * There is at most one DRAFT row per (client, app). MySQL has no partial unique
 * index, so that is enforced in ClientUrlService rather than by a constraint.
 */
public enum ClientUrlType {
    LIVE,
    DRAFT;

    public static ClientUrlType from(Object type) {
        return type == null ? LIVE : ClientUrlType.valueOf(type.toString());
    }
}
