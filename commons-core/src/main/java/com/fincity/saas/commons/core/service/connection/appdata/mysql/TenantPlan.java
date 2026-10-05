package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import java.util.List;

/**
 * One tenant's migration: what its table looks like now, what the definition says it
 * should look like, and the plan between the two.
 *
 * Per tenant rather than shared, because the storage definition is overridable per
 * client. SYSTEM, a mid-level client and a leaf client resolve to different merged
 * schemas and therefore different tables, so both sides of the diff differ and a
 * publish is N plans rather than one plan applied N times.
 *
 * The shape is the fingerprint of what this tenant's table should look like, and it
 * is what the journal keys on. See {@link MySQLTablePlanner#shapeOf}.
 */
public record TenantPlan(String database, String shape, List<SchemaChange> changes, List<MigrationStep> steps) {

    public TenantPlan(String database, List<SchemaChange> changes, List<MigrationStep> steps) {
        this(database, "", changes, steps);
    }

    public boolean isNoOp() {
        return this.steps.isEmpty();
    }

    /** True when something here could fail or lose data, so it must be pre-flighted. */
    public boolean needsCheck() {
        return this.changes.stream().anyMatch(SchemaChange::needsDataCheck);
    }
}
