package com.modlix.saas.adzump.service.integration;

import com.modlix.saas.adzump.model.leadzump.Stage;
import com.modlix.saas.adzump.model.leadzump.Status;

/**
 * One item of integration drift: a leadzump stage/status or a product that was added (needs mapping)
 * or removed (a mapping to retire) between the last-confirmed EP shape and the live one. Part of a
 * {@link DriftReport}.
 *
 * <p>{@code templateId} scopes stage/status changes to their template; it is the product's template
 * for a {@code PRODUCT} add and {@code null} for a {@code PRODUCT} removal (its template is unknown
 * once the product is gone from EP).
 */
public record Change(Kind kind, String templateId, String key, String name) {

    public enum Kind {
        STAGE, STATUS, PRODUCT
    }

    public static Change stage(String templateId, Stage stage) {
        return new Change(Kind.STAGE, templateId, stage.getKey(), stage.getName());
    }

    public static Change status(String templateId, Status status) {
        return new Change(Kind.STATUS, templateId, status.getKey(), status.getName());
    }

    public static Change product(String templateId, String productId, String name) {
        return new Change(Kind.PRODUCT, templateId, productId, name);
    }

    /**
     * A removed pipeline key. The source type (stage vs status) is not recorded in the stored mapping
     * body, so the caller classifies best-effort (a junk key is a status; otherwise a stage).
     */
    public static Change removed(Kind kind, String templateId, String key) {
        return new Change(kind, templateId, key, null);
    }
}
