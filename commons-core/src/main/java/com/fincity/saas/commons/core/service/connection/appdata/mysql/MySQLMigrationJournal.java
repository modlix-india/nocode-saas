package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import java.util.List;

import org.jooq.DSLContext;

import reactor.core.publisher.Mono;

/**
 * The record of how far a migration got, written before the first statement.
 *
 * MySQL cannot roll a DDL statement back, so after a crash the only question that
 * matters is how far this got, and the only place that can answer it is a row written
 * ahead of the work. It lives in the tenant schema rather than centrally so it travels
 * with the thing it describes and cannot drift from it.
 *
 * The row is also what a retry continues from. One row per (storage, version) per
 * tenant, carried across attempts rather than re-inserted, so the history of a
 * migration that took three goes to land is one row saying so rather than three rows
 * that have to be ordered by hand.
 */
public final class MySQLMigrationJournal {

    public static final String TABLE = "storage_migration";

    private MySQLMigrationJournal() {
    }

    public static String createTable(String db) {
        return "CREATE TABLE IF NOT EXISTS `" + db + "`.`" + TABLE + "` ("
                + "`id` CHAR(26) NOT NULL,"
                + "`storage_name` VARCHAR(255) NOT NULL,"
                + "`table_name` VARCHAR(255) NOT NULL,"
                + "`from_version` INT NULL,"
                + "`to_version` INT NOT NULL,"
                + "`surface` VARCHAR(16) NOT NULL,"
                // The migration's identity. See MySQLTablePlanner.shapeOf for why this
                // and not the version.
                + "`shape` CHAR(32) NOT NULL,"
                + "`phase` VARCHAR(20) NULL,"
                // How far: the step last attempted, out of how many, and how many of
                // them actually executed across every attempt.
                + "`statement_index` INT NOT NULL DEFAULT 0,"
                + "`total_statements` INT NOT NULL,"
                + "`applied_count` INT NOT NULL DEFAULT 0,"
                + "`attempt` INT NOT NULL DEFAULT 1,"
                + "`state` VARCHAR(20) NOT NULL,"
                + "`started_at` DATETIME(3) NULL,"
                + "`resumed_at` DATETIME(3) NULL,"
                + "`finished_at` DATETIME(3) NULL,"
                + "`error` TEXT NULL,"
                + "`applied_by` VARCHAR(255) NULL,"
                // The statements themselves. Recovery replays these rather than
                // re-planning, because a fresh diff after a partial apply is a
                // DIFFERENT plan and the recorded index would point into it.
                + "`plan` MEDIUMTEXT NULL,"
                // Who is driving this row, and when they last said so. The claim is
                // what stops two nodes resuming the same migration.
                + "`claimed_by` VARCHAR(255) NULL,"
                + "`claimed_at` DATETIME(3) NULL,"
                // The concurrency guard. NULL unless the row is live, and a
                // unique index ignores NULLs - so at most one unfinished row can
                // exist per storage, shape and surface, and the second of two
                // simultaneous publishes is refused by the database rather than
                // by a count that another transaction can invalidate between
                // reading it and acting on it.
                //
                // PLANNED as well as RUNNING, because the INSERT is the claim:
                // a row is written PLANNED and only becomes RUNNING once the
                // first statement goes out, so guarding RUNNING alone would
                // leave the window it exists to close wide open.
                + "`active_key` VARCHAR(320) GENERATED ALWAYS AS (IF(`state` IN ('PLANNED', 'RUNNING'),"
                + " CONCAT(`storage_name`, ':', `shape`, ':', `surface`), NULL)) STORED,"
                + "PRIMARY KEY (`id`),"
                + "UNIQUE KEY `uk_active_migration` (`active_key`),"
                // The fail-closed lookup runs on every publish, so it gets an index.
                + "KEY `idx_storage_state` (`storage_name`, `state`),"
                // And so does the resume lookup, which is by storage and shape.
                + "KEY `idx_storage_shape` (`storage_name`, `shape`),"
                // The recovery sweep's only query: unfinished rows, oldest claim first.
                + "KEY `idx_state_claim` (`state`, `claimed_at`)"
                + ")";
    }

    public static Mono<Void> ensure(DSLContext ctx, String db) {
        return Mono.from(ctx.query(createTable(db)))
                .thenMany(reactor.core.publisher.Flux.fromIterable(upgrades(db))
                        // Tolerated one by one. Adding the unique guard fails on a
                        // table that already holds two RUNNING rows for one storage
                        // - which is the very state it prevents in future - and
                        // refusing to start over an old duplicate would make the
                        // guard worse than not having it.
                        .concatMap(sql -> Mono.from(ctx.query(sql)).onErrorResume(e -> Mono.empty())))
                .then();
    }

    /**
     * Columns an older journal table is missing.
     *
     * {@code CREATE TABLE IF NOT EXISTS} does nothing to a table that already
     * exists, so a tenant whose journal predates a column would go on working until
     * the first statement that names one - and the first such statement is inside
     * recovery, which is not where anyone wants to discover a schema problem.
     *
     * Each is guarded by its own existence check rather than run blindly: MySQL has
     * no ADD COLUMN IF NOT EXISTS, and this runs before every migration.
     */
    static List<String> upgrades(String db) {

        return List.of(
                addColumn(db, "plan", "MEDIUMTEXT NULL"),
                addColumn(db, "claimed_by", "VARCHAR(255) NULL"),
                addColumn(db, "claimed_at", "DATETIME(3) NULL"),
                // Nullable on an upgrade, where the live table says NOT NULL. A row
                // written before shapes existed has no shape, and saying so is more
                // use than inventing an empty one that could collide with a real
                // query's key. It also keeps a quoted default out of a statement
                // that is itself built as a quoted string.
                addColumn(db, "shape", "CHAR(32) NULL"),
                addColumn(db, "applied_count", "INT NOT NULL DEFAULT 0"),
                addColumn(db, "attempt", "INT NOT NULL DEFAULT 1"),
                addColumn(db, "resumed_at", "DATETIME(3) NULL"),
                addColumn(
                        db,
                        "active_key",
                        "VARCHAR(320) GENERATED ALWAYS AS (IF(`state` IN ('PLANNED', 'RUNNING'),"
                                + " CONCAT(`storage_name`, ':', `shape`, ':', `surface`), NULL)) STORED"),
                addUniqueIndex(db, "uk_active_migration", "active_key"));
    }

    /**
     * Idempotent without ADD COLUMN IF NOT EXISTS, which MySQL 8 does not have.
     *
     * A prepared statement built from information_schema, executed only when the
     * column is absent. The same trick the migration planner's preconditions use.
     */
    /**
     * The same guarded trick for an index.
     *
     * Adding it can fail on a table that already holds two RUNNING rows for one
     * storage - exactly the state this prevents in future. The caller tolerates
     * that: refusing to start because an old duplicate exists would make the
     * guard worse than its absence.
     */
    private static String addUniqueIndex(String db, String index, String column) {
        return "SET @s = (SELECT IF(COUNT(*) > 0, 'SELECT 1',"
                + " 'ALTER TABLE `" + db + "`.`" + TABLE + "` ADD UNIQUE KEY `" + index + "` (`" + column
                + "`)')"
                + " FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = '" + db + "' AND TABLE_NAME = '"
                + TABLE + "' AND INDEX_NAME = '" + index + "');"
                + " PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;";
    }

    private static String addColumn(String db, String column, String definition) {
        return "SET @s = (SELECT IF(COUNT(*) > 0, 'SELECT 1',"
                + " 'ALTER TABLE `" + db + "`.`" + TABLE + "` ADD COLUMN `" + column + "` " + definition + "')"
                + " FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = '" + db + "' AND TABLE_NAME = '" + TABLE
                + "' AND COLUMN_NAME = '" + column + "');"
                + " PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;";
    }

    /**
     * Where this exact migration got to here, or empty if it has never been attempted.
     *
     * This is the whole resume story. Step preconditions make each STATEMENT
     * re-runnable, which is what lets an interrupted plan be finished. They are not
     * enough on their own for two separate reasons, and the row answers both.
     *
     * A finished plan would otherwise repeat: once CONTRACT has renamed the temporary
     * column into place, EXPAND's precondition sees it absent again and the whole
     * expand-contract sequence runs a second time, dropping and recreating a column
     * that was already correct.
     *
     * And a plan interrupted DURING contract cannot be restarted from the beginning at
     * all: the old column has already been dropped, so the pre-flight check that reads
     * it is no longer a valid statement. Only the recorded index says where it is safe
     * to pick up.
     */
    public static Mono<JournalEntry> findAt(DSLContext ctx, String db, String storageName, String shape) {
        return find(ctx, db, "`storage_name` = '" + escape(storageName) + "' AND `shape` = '" + escape(shape) + "'");
    }

    /**
     * The most recent migration of this storage here, whatever shape it was for.
     *
     * For reporting rather than for deciding: "how far did this get" is asked about
     * the storage, by someone who does not know which shape was in flight when it
     * stopped.
     */
    public static Mono<JournalEntry> findLatest(DSLContext ctx, String db, String storageName) {
        return find(ctx, db, "`storage_name` = '" + escape(storageName) + "'");
    }

    private static Mono<JournalEntry> find(DSLContext ctx, String db, String where) {

        String sql = "SELECT `id`, `state`, `statement_index`, `attempt`, `applied_count`, `total_statements` FROM `"
                + db + "`.`" + TABLE + "` WHERE " + where + " ORDER BY `started_at` DESC, `id` DESC LIMIT 1";

        return Mono.from(ctx.resultQuery(sql))
                .map(r -> new JournalEntry(
                        String.valueOf(r.get(0)),
                        MigrationState.valueOf(String.valueOf(r.get(1))),
                        intOf(r.get(2)),
                        intOf(r.get(3)),
                        intOf(r.get(4)),
                        intOf(r.get(5))));
    }

    /**
     * Migrations that must be resolved by hand before this storage may be published
     * again, which deliberately does NOT include this same migration.
     *
     * The thing fail-closed exists to prevent is stacking a DIFFERENT migration on an
     * unfinished one, because that is where the two half-applied plans interleave and
     * data actually gets lost. Re-running the SAME plan is the opposite: it is the
     * documented recovery path, and blocking it would mean every transient failure
     * needed an operator to edit a journal row before the retry could even be tried.
     *
     * NEEDS_ATTENTION blocks at any version, including this one. It is only ever set by
     * a human, and it means exactly what it says.
     */
    public static String blockingCount(String db, String storageName, String shape) {
        return "SELECT COUNT(*) FROM `" + db + "`.`" + TABLE + "` WHERE `storage_name` = '" + escape(storageName)
                + "' AND ((`state` IN ('" + MigrationState.FAILED + "', '" + MigrationState.RUNNING
                + "') AND `shape` <> '" + escape(shape) + "') OR `state` = '" + MigrationState.NEEDS_ATTENTION + "')";
    }

    static String insert(
            String db,
            String id,
            String storageName,
            String table,
            Integer fromVersion,
            int toVersion,
            String shape,
            String surface,
            int totalStatements,
            String appliedBy,
            String planJson,
            String node) {

        return "INSERT INTO `" + db + "`.`" + TABLE + "` (`id`, `storage_name`, `table_name`, `from_version`,"
                + " `to_version`, `shape`, `surface`, `statement_index`, `total_statements`, `applied_count`,"
                + " `attempt`, `state`, `started_at`, `applied_by`, `plan`, `claimed_by`, `claimed_at`) VALUES ('"
                + id + "', '" + escape(storageName) + "', '" + escape(table) + "', "
                + (fromVersion == null ? "NULL" : fromVersion) + ", " + toVersion + ", '" + escape(shape) + "', '"
                + surface + "', 0, " + totalStatements + ", 0, 1, '" + MigrationState.PLANNED + "', NOW(3), "
                + (appliedBy == null ? "NULL" : "'" + escape(appliedBy) + "'") + ", '" + escape(planJson) + "', "
                + (node == null ? "NULL" : "'" + escape(node) + "'") + ", NOW(3))";
    }

    /**
     * Reopen an existing row for another go, rather than inserting a second one.
     *
     * The error from the previous attempt is cleared here and not before: until this
     * statement runs, the row still reads as the failure it was, which is what makes
     * the journal safe to look at while a retry is in flight.
     */
    static String reopen(String db, String id, int totalStatements, String appliedBy) {
        return "UPDATE `" + db + "`.`" + TABLE + "` SET `state` = '" + MigrationState.RUNNING + "', `attempt` ="
                + " `attempt` + 1, `resumed_at` = NOW(3), `finished_at` = NULL, `error` = NULL, `total_statements` = "
                + totalStatements + (appliedBy == null ? "" : ", `applied_by` = '" + escape(appliedBy) + "'")
                + " WHERE `id` = '" + id + "'";
    }

    /**
     * Progress, which doubles as the heartbeat.
     *
     * It already runs before every statement, so touching {@code claimed_at} here
     * costs nothing and means a migration that is genuinely in flight keeps its claim
     * without any separate timer. A row whose claim has gone stale is one whose
     * driver stopped, which is exactly what the sweep is looking for.
     */
    static String progress(
            String db, String id, MigrationState state, int index, MigrationStep.Phase phase, int appliedCount) {
        return "UPDATE `" + db + "`.`" + TABLE + "` SET `state` = '" + state + "', `statement_index` = " + index
                + ", `applied_count` = " + appliedCount + ", `claimed_at` = NOW(3), `phase` = "
                + (phase == null ? "NULL" : "'" + phase + "'") + " WHERE `id` = '" + id + "'";
    }

    /**
     * Take ownership of an interrupted row, if nobody else already has.
     *
     * The guard is in the WHERE clause rather than in a read followed by a write,
     * because two nodes starting a sweep at the same moment is the normal case during
     * a rolling deploy - which is also precisely when interrupted rows exist. MySQL
     * reports how many rows the statement changed, and exactly one claimant sees 1.
     */
    static String claim(String db, String id, String node, int staleAfterSeconds) {
        return "UPDATE `" + db + "`.`" + TABLE + "` SET `claimed_by` = '" + escape(node) + "', `claimed_at` ="
                + " NOW(3) WHERE `id` = '" + id + "' AND `state` = '" + MigrationState.RUNNING + "' AND"
                + " (`claimed_at` IS NULL OR `claimed_at` < NOW(3) - INTERVAL " + staleAfterSeconds + " SECOND)";
    }

    /**
     * Rows nobody is driving any more.
     *
     * RUNNING only, deliberately. RUNNING means a process was part way through and
     * stopped - a deploy, a crash, a kill - so re-running it is the whole recovery
     * story. FAILED means it WAS driven and refused, usually by a data check that
     * will refuse again; sweeping those would retry a known-bad migration on a timer
     * and bury the real one in noise. They wait for a person, or for the next publish.
     */
    static String unfinished(String db, int staleAfterSeconds) {
        return "SELECT `id`, `storage_name`, `table_name`, `shape`, `surface`, `statement_index`,"
                + " `applied_count`, `attempt`, `total_statements`, `plan` FROM `" + db + "`.`" + TABLE + "`"
                // PLANNED as well as RUNNING. Now that a live row blocks the next
                // publish of the same shape, one left behind by a crash between the
                // insert and the first statement has to be reachable by recovery, or
                // it blocks that storage for ever.
                + " WHERE `state` IN ('" + MigrationState.PLANNED + "', '" + MigrationState.RUNNING
                + "') AND (`claimed_at` IS NULL OR `claimed_at` <"
                + " NOW(3) - INTERVAL " + staleAfterSeconds + " SECOND) ORDER BY `claimed_at` ASC";
    }

    /** Every tenant schema on this server that has ever run a migration. */
    static String schemasWithJournal() {
        return "SELECT TABLE_SCHEMA FROM information_schema.TABLES WHERE TABLE_NAME = '" + TABLE + "'"
                + " ORDER BY TABLE_SCHEMA";
    }

    static String finish(String db, String id, MigrationState state, String error, int appliedCount) {
        return "UPDATE `" + db + "`.`" + TABLE + "` SET `state` = '" + state + "', `finished_at` = NOW(3),"
                + " `applied_count` = " + appliedCount + ", `error` = "
                + (error == null ? "NULL" : "'" + escape(error) + "'") + " WHERE `id` = '" + id + "'";
    }

    private static int intOf(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    /**
     * These statements are assembled as text rather than bound, because they are issued
     * alongside DDL which cannot be parameterised. Only the error message, the acting
     * user and the identifiers are free text, and all are escaped here.
     */
    static String escape(String s) {
        return s == null ? null : s.replace("\\", "\\\\").replace("'", "''");
    }
}
