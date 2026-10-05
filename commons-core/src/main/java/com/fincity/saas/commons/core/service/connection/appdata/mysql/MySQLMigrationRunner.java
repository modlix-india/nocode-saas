package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import java.util.List;

import org.jooq.DSLContext;

import com.fincity.saas.commons.util.UniqueUtil;

import reactor.core.publisher.Mono;

/**
 * Executes a migration plan against one tenant, journalling as it goes.
 *
 * Four rules, and all four exist because MySQL cannot roll a DDL statement back.
 *
 * The journal row is written BEFORE the first statement, so a crash leaves a record of
 * how far this got rather than nothing at all.
 *
 * Every step is guarded by a precondition, so statements already applied are skipped
 * rather than failing.
 *
 * An interrupted run of the SAME plan is resumed from the step it stopped on, against
 * the same journal row. Preconditions alone cannot do this: once CONTRACT has dropped
 * the old column, the pre-flight check that reads that column is no longer a valid
 * statement, so a restart from the beginning fails on a plan that was nearly done.
 *
 * A half-applied run of a DIFFERENT plan blocks outright. Stacking a second migration
 * on a broken first one is where data actually gets lost, and that - not the retry -
 * is what fail-closed is for. "Same" and "different" are decided by the table shape
 * the plan is heading for, not by the storage's version number: a schema the storage
 * references can change without the version moving at all.
 */
public final class MySQLMigrationRunner {

    /**
     * Who holds a claim, for the journal to record.
     *
     * The hostname plus the JVM's start time, so two processes on one host are told
     * apart and a restarted process does not inherit its predecessor's claim - which
     * is the exact case recovery exists for.
     */
    public static final String NODE = node();

    private MySQLMigrationRunner() {
    }

    private static String node() {
        String host;
        try {
            host = java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "unknown";
        }
        return host + ":" + java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();
    }

    public static Mono<MigrationOutcome> run(
            DSLContext ctx,
            String db,
            String storageName,
            String table,
            Integer fromVersion,
            int toVersion,
            String shape,
            String surface,
            List<MigrationStep> plan,
            String appliedBy) {

        if (plan == null || plan.isEmpty()) return Mono.just(MigrationOutcome.applied(0, 0));

        return MySQLMigrationJournal.ensure(ctx, db)
                .then(count(ctx, MySQLMigrationJournal.blockingCount(db, storageName, shape)))
                .flatMap(blocking -> blocking > 0
                        ? Mono.just(MigrationOutcome.needsAttention("storage " + storageName + " has " + blocking
                                + " unresolved migration(s) to a different shape; resolve them before publishing"
                                + " again"))
                        : MySQLMigrationJournal.findAt(ctx, db, storageName, shape)
                                .flatMap(entry -> continueFrom(ctx, db, entry, plan, appliedBy))
                                .switchIfEmpty(Mono.defer(() -> fresh(
                                        ctx, db, storageName, table, fromVersion, toVersion, shape, surface, plan,
                                        appliedBy))));
    }

    /**
     * There is already a row for this storage at this version.
     *
     * Complete means the plan is a no-op, however hard the preconditions would argue
     * otherwise. Resumable means pick up at the recorded step. Anything else -
     * ABANDONED - is written off, and the next publish starts clean.
     */
    private static Mono<MigrationOutcome> continueFrom(
            DSLContext ctx, String db, JournalEntry entry, List<MigrationStep> plan, String appliedBy) {

        if (entry.isComplete()) return Mono.just(MigrationOutcome.applied(0, plan.size()));

        if (!entry.isResumable()) return Mono.empty();

        // Progress is written before each statement, so the recorded index is the step
        // that was ATTEMPTED, not the last one that succeeded. Re-attempting it is the
        // point: its precondition decides whether it actually needs to run.
        int from = Math.max(0, Math.min(entry.statementIndex(), plan.size() - 1));

        return Mono.from(ctx.query(MySQLMigrationJournal.reopen(db, entry.id(), plan.size(), appliedBy)))
                .then(execute(ctx, db, entry.id(), plan, from, entry.appliedCount()))
                .map(outcome -> outcome.resumedFrom(from))
                .onErrorResume(e -> fail(ctx, db, entry.id(), entry.appliedCount(), e));
    }

    /**
     * Whether this failure is the concurrency guard rather than a real problem.
     *
     * Matched on the duplicate-key signal rather than the message text where
     * possible; the text fallback is there because the driver wraps the error
     * differently depending on whether the statement went through jOOQ.
     */
    static boolean alreadyRunning(Throwable e) {

        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof org.jooq.exception.IntegrityConstraintViolationException) return true;

            String message = t.getMessage();
            if (message != null && message.contains("uk_active_migration")) return true;
        }

        return false;
    }

    private static Mono<MigrationOutcome> fresh(
            DSLContext ctx,
            String db,
            String storageName,
            String table,
            Integer fromVersion,
            int toVersion,
            String shape,
            String surface,
            List<MigrationStep> plan,
            String appliedBy) {

        String id = UniqueUtil.ulid();

        return Mono.from(ctx.query(MySQLMigrationJournal.insert(
                        db, id, storageName, table, fromVersion, toVersion, shape, surface, plan.size(), appliedBy,
                        MigrationPlanCodec.write(plan), NODE)))
                .then(execute(ctx, db, id, plan, 0, 0))
                // A duplicate on the active key means another publish inserted the
                // RUNNING row between this one counting blockers and writing its
                // own. The count cannot close that window - only the unique index
                // can - so losing the race is an ordinary outcome and not a
                // failure: the other run is doing exactly this work.
                .onErrorResume(MySQLMigrationRunner::alreadyRunning, e -> Mono.just(MigrationOutcome.inProgressElsewhere(
                                "another publish is already applying this shape to " + storageName)))
                .onErrorResume(e -> fail(ctx, db, id, 0, e));
    }

    private static Mono<MigrationOutcome> fail(
            DSLContext ctx, String db, String id, int appliedBefore, Throwable e) {
        return Mono.from(ctx.query(
                        MySQLMigrationJournal.finish(db, id, MigrationState.FAILED, message(e), appliedBefore)))
                .thenReturn(MigrationOutcome.failed(message(e), 0, 0));
    }

    /**
     * Walks the plan one step at a time, then closes the row off.
     *
     * Sequential on purpose: each step's precondition is evaluated against the state the
     * previous step left behind, so there is nothing to parallelise and a great deal to
     * get wrong by trying.
     */
    /**
     * Continue a row the recovery sweep has already claimed.
     *
     * Separate from {@link #run} because the sweep works from the journal alone: it
     * has the plan and the index out of the row, and deliberately never resolves a
     * storage definition. Recovery runs when the rest of the system is in a state
     * nobody planned, so needing less of it is the point.
     */
    public static Mono<MigrationOutcome> resume(
            DSLContext ctx, String db, String id, List<MigrationStep> plan, int statementIndex, int appliedSoFar) {

        if (plan == null || plan.isEmpty())
            return Mono.just(MigrationOutcome.failed("the journal row carries no replayable plan", 0, 0));

        int from = Math.max(0, Math.min(statementIndex, plan.size() - 1));

        return Mono.from(ctx.query(MySQLMigrationJournal.reopen(db, id, plan.size(), null)))
                .then(execute(ctx, db, id, plan, from, appliedSoFar))
                .map(outcome -> outcome.resumedFrom(from))
                .onErrorResume(e -> fail(ctx, db, id, appliedSoFar, e));
    }

    private static Mono<MigrationOutcome> execute(
            DSLContext ctx, String db, String id, List<MigrationStep> plan, int from, int appliedBefore) {

        return step(ctx, db, id, plan, from, 0, from, appliedBefore)
                .flatMap(outcome -> Mono.from(ctx.query(MySQLMigrationJournal.finish(
                                db, id, outcome.state(), outcome.error(), appliedBefore + outcome.applied())))
                        .thenReturn(outcome));
    }

    private static Mono<MigrationOutcome> step(
            DSLContext ctx,
            String db,
            String id,
            List<MigrationStep> plan,
            int index,
            int applied,
            int skipped,
            int appliedBefore) {

        if (index >= plan.size()) return Mono.just(MigrationOutcome.applied(applied, skipped));

        MigrationStep s = plan.get(index);

        return Mono.from(ctx.query(MySQLMigrationJournal.progress(
                        db, id, MigrationState.RUNNING, index, s.phase(), appliedBefore + applied)))
                .then(Mono.defer(() -> {

                    // A check step has no SQL of its own: its whole job is to refuse.
                    if (s.dataCheck() != null)
                        return count(ctx, s.dataCheck())
                                .flatMap(offending -> offending > 0
                                        ? Mono.just(MigrationOutcome.blocked(
                                                s.dataCheck(), offending, applied, skipped))
                                        : next(ctx, db, id, plan, index, applied, skipped, appliedBefore, s));

                    return next(ctx, db, id, plan, index, applied, skipped, appliedBefore, s);
                }));
    }

    private static Mono<MigrationOutcome> next(
            DSLContext ctx,
            String db,
            String id,
            List<MigrationStep> plan,
            int index,
            int applied,
            int skipped,
            int appliedBefore,
            MigrationStep s) {

        if (s.sql() == null) return step(ctx, db, id, plan, index + 1, applied, skipped, appliedBefore);

        Mono<Boolean> shouldRun = s.precondition() == null
                ? Mono.just(Boolean.TRUE)
                : count(ctx, s.precondition()).map(c -> c > 0);

        return shouldRun.flatMap(run -> run
                ? Mono.from(ctx.query(s.sql()))
                        .then(step(ctx, db, id, plan, index + 1, applied + 1, skipped, appliedBefore))
                : step(ctx, db, id, plan, index + 1, applied, skipped + 1, appliedBefore));
    }

    private static Mono<Long> count(DSLContext ctx, String sql) {
        return Mono.from(ctx.resultQuery(sql))
                .map(r -> r.get(0) instanceof Number n ? n.longValue() : 0L)
                .defaultIfEmpty(0L);
    }

    private static String message(Throwable e) {
        String m = e.getMessage();
        if (m == null) return e.getClass().getSimpleName();
        return m.length() > 900 ? m.substring(0, 900) : m;
    }
}
