package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.jooq.DSLContext;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Applies a storage definition change across every tenant that holds it.
 *
 * The shape of this is dictated by one fact: a definition is shared by up to 71 tenant
 * schemas, each non-transactional and each able to fail on its own. So:
 *
 * every tenant is pre-flighted BEFORE any is touched, because learning that three
 * tenants would fail is worth far more before the other 68 have been migrated than
 * after;
 *
 * tenants are then migrated independently and journalled separately, so one failure
 * does not block the rest;
 *
 * and the result is progress rather than success, because partial completion across a
 * fan-out is normal.
 */
public final class MySQLFanOut {

    private MySQLFanOut() {
    }

    /**
     * Build one plan per tenant.
     *
     * {@code desiredFor} resolves the columns that tenant's client should have, which
     * is per-client because the definition is overridable: a descendant that pinned its
     * own type for a field is unaffected by the base changing it, and gets an empty
     * plan rather than the same plan as everyone else.
     */
    public static Mono<List<TenantPlan>> plan(
            DSLContext ctx,
            List<String> tenants,
            String table,
            int toVersion,
            Function<String, List<MySQLColumn>> desiredFor) {

        return Flux.fromIterable(tenants)
                .concatMap(db -> MySQLTableInspector.tableExists(ctx, db, table)
                        .flatMap(exists -> !exists
                                // A tenant with no table yet is not a migration; the
                                // ordinary lazy create will make it at the right shape.
                                ? Mono.just(new TenantPlan(db, List.of(), List.of()))
                                : MySQLTableInspector.columns(ctx, db, table)
                                        .map(existing -> {
                                            List<MySQLColumn> desired = desiredFor.apply(db);
                                            List<SchemaChange> changes = MySQLTablePlanner.diff(existing, desired);
                                            return new TenantPlan(
                                                    db,
                                                    MySQLTablePlanner.shapeOf(desired),
                                                    changes,
                                                    MySQLMigrationPlanner.plan(db, table, toVersion, changes));
                                        })))
                .collectList();
    }

    /**
     * Run every data check without issuing a single DDL statement.
     *
     * This is the step that makes a 71-way fan-out survivable. The checks are the ones
     * the planner marked as pre-flightable, which means they read only columns that
     * already exist.
     */
    public static Mono<Map<String, Long>> preflight(DSLContext ctx, List<TenantPlan> plans) {

        Map<String, Long> blocked = new LinkedHashMap<>();

        return Flux.fromIterable(plans)
                .concatMap(p -> Flux.fromIterable(p.steps())
                        .filter(s -> s.phase() == MigrationStep.Phase.PREFLIGHT && s.dataCheck() != null)
                        .concatMap(s -> count(ctx, s.dataCheck()))
                        .reduce(0L, Long::sum)
                        .doOnNext(offending -> {
                            if (offending > 0) blocked.put(p.database(), offending);
                        }))
                .then(Mono.just(blocked));
    }

    /**
     * Pre-flight, then migrate only the tenants that are clean.
     *
     * A blocked tenant is reported rather than attempted. Running it anyway would stop
     * at the same check a moment later, having already written a failed journal row
     * that then blocks the storage until someone clears it.
     *
     * Re-running this whole call is the recovery path, and it is cheap: a tenant the
     * journal already records as applied returns immediately, a tenant that stopped
     * part way picks up at the step it stopped on, and a tenant that was never reached
     * runs for the first time. Nothing has to be worked out by hand first, which is
     * the point - a fan-out that can only be recovered by someone deciding which of 71
     * schemas to re-run is not recoverable in practice.
     */
    public static Mono<FanOutReport> apply(
            DSLContext ctx,
            List<TenantPlan> plans,
            String storageName,
            String table,
            Integer fromVersion,
            int toVersion,
            String surface,
            String appliedBy) {

        List<TenantPlan> work = plans.stream().filter(p -> !p.isNoOp()).toList();
        List<String> noOp = plans.stream()
                .filter(TenantPlan::isNoOp)
                .map(TenantPlan::database)
                .toList();

        return preflight(ctx, work).flatMap(blocked -> {

            List<TenantPlan> clean =
                    work.stream().filter(p -> !blocked.containsKey(p.database())).toList();

            Map<String, MigrationOutcome> outcomes = new LinkedHashMap<>();
            blocked.forEach((db, offending) ->
                    outcomes.put(db, MigrationOutcome.blocked("pre-flight", offending, 0, 0)));

            return Flux.fromIterable(clean)
                    .concatMap(p -> MySQLMigrationRunner.run(
                                    ctx,
                                    p.database(),
                                    storageName,
                                    table,
                                    fromVersion,
                                    toVersion,
                                    p.shape(),
                                    surface,
                                    p.steps(),
                                    appliedBy)
                            .doOnNext(o -> outcomes.put(p.database(), o)))
                    .then(Mono.fromSupplier(() -> {
                        List<String> blockedDbs = new ArrayList<>(blocked.keySet());
                        List<String> resumed = new ArrayList<>();
                        outcomes.forEach((db, o) -> {
                            if (!o.isSuccess() && !blockedDbs.contains(db)) blockedDbs.add(db);
                            if (o.wasResumed()) resumed.add(db);
                        });
                        return new FanOutReport(outcomes, blockedDbs, noOp, resumed);
                    }));
        });
    }

    /**
     * How far a publish got, read back from the tenants' own journals.
     *
     * Deliberately separate from {@link #apply}: the question "how far did we get" is
     * usually asked after something went wrong, often in a different process from the
     * one that was running at the time, and an answer that only exists in a return
     * value nobody kept is no answer at all. Each tenant's journal is the record, so
     * this only has to read it.
     *
     * A tenant with no row has not been attempted at this version. That is not the
     * same as having nothing to do, and the two are not distinguished here, because
     * from the journal they are genuinely indistinguishable: the plan is what knows,
     * and the plan is not in the journal.
     */
    public static Mono<List<TenantProgress>> status(DSLContext ctx, List<String> tenants, String storageName) {

        return Flux.fromIterable(tenants)
                .concatMap(db -> MySQLTableInspector
                        .tableExists(ctx, db, MySQLMigrationJournal.TABLE)
                        .flatMap(hasJournal -> !hasJournal
                                ? Mono.just(new TenantProgress(db, null, 0, 0, 0))
                                : MySQLMigrationJournal.findLatest(ctx, db, storageName)
                                        .map(e -> new TenantProgress(
                                                db, e.state(), e.statementIndex(), e.total(), e.attempt()))
                                        .defaultIfEmpty(new TenantProgress(db, null, 0, 0, 0))))
                .collectList();
    }

    private static Mono<Long> count(DSLContext ctx, String sql) {
        return Mono.from(ctx.resultQuery(sql))
                .map(r -> r.get(0) instanceof Number n ? n.longValue() : 0L)
                .defaultIfEmpty(0L);
    }
}
