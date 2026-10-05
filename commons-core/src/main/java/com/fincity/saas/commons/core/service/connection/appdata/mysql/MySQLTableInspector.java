package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import java.util.List;

import org.jooq.DSLContext;

import com.fincity.saas.commons.core.service.connection.appdata.IAppDataService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reads a tenant's table as it actually is, which is one half of every migration diff.
 *
 * The other half comes from the storage definition, and because the definition is
 * overridable per client, neither half is shared across tenants. A publish is N
 * different diffs, not one plan applied N times.
 */
public final class MySQLTableInspector {

    private MySQLTableInspector() {
    }

    /** The columns MySQL reports, in the shape the planner compares against. */
    public static Mono<List<MySQLColumn>> columns(DSLContext ctx, String db, String table) {

        String sql = "SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLLATION_NAME FROM information_schema.COLUMNS"
                + " WHERE TABLE_SCHEMA = '" + db + "' AND TABLE_NAME = '" + table + "'"
                + " ORDER BY COLUMN_NAME";

        return Flux.from(ctx.resultQuery(sql))
                .map(r -> new MySQLColumn(
                        String.valueOf(r.get(0)),
                        // COLUMN_TYPE is reported lowercase; the planner compares
                        // case-insensitively, so it is left as MySQL gave it.
                        String.valueOf(r.get(1)),
                        "YES".equalsIgnoreCase(String.valueOf(r.get(2))),
                        null,
                        // Null for every non-textual column, and the table default for
                        // a textual one that never asked for anything. The planner only
                        // compares this when the DESIRED column declares a collation,
                        // so a server default never looks like a change.
                        r.get(3) == null ? null : String.valueOf(r.get(3))))
                .filter(c -> !MySQLTypeMapper.ID_COLUMN.equals(c.name()))
                // Temporary columns from an interrupted migration are not part of the
                // table's declared shape and must not look like columns to drop.
                .filter(c -> !isMigrationTemp(c.name()))
                .collectList();
    }

    public static Mono<Boolean> tableExists(DSLContext ctx, String db, String table) {
        return Mono.from(ctx.resultQuery("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = '" + db
                        + "' AND TABLE_NAME = '" + table + "'"))
                .map(r -> r.get(0) instanceof Number n && n.intValue() > 0)
                .defaultIfEmpty(Boolean.FALSE);
    }

    /**
     * Every tenant schema holding this table.
     *
     * Derived from what exists rather than from a list of clients, so a tenant that was
     * provisioned outside the normal path is still migrated instead of being silently
     * left on an old shape.
     */
    public static Mono<List<String>> tenantsWithTable(DSLContext ctx, String appCode, String table) {

        String sql = "SELECT TABLE_SCHEMA FROM information_schema.TABLES WHERE TABLE_NAME = '" + table + "'"
                + " AND (TABLE_SCHEMA LIKE '%\\_" + appCode + "' OR TABLE_SCHEMA LIKE '%\\_" + appCode
                + IAppDataService.DRAFT_DB_SUFFIX + "') ORDER BY TABLE_SCHEMA";

        return Flux.from(ctx.resultQuery(sql))
                .map(r -> String.valueOf(r.get(0)))
                .collectList();
    }

    static boolean isMigrationTemp(String column) {
        int i = column.lastIndexOf(MySQLMigrationPlanner.NEW_SUFFIX);
        if (i < 0) return false;
        String tail = column.substring(i + MySQLMigrationPlanner.NEW_SUFFIX.length());
        return !tail.isEmpty() && tail.chars().allMatch(Character::isDigit);
    }
}
