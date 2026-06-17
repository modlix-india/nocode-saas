package com.fincity.security.model.billing;

/**
 * Domain-level action class (mirrors the per-app / catalog JOOQ enums). Drives
 * zero-balance behavior: ENGAGEMENT actions get grace, METERED actions block.
 */
public enum ActionClass {
    ENGAGEMENT,
    METERED
}
