package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Finds migrations nobody is driving and finishes them.
 *
 * Until this existed, an interrupted migration waited for somebody to publish that
 * storage again. A rolling deploy that killed a node mid-migration left the tenant
 * half-migrated with nothing watching and nothing saying so - and for the one
 * statement that spans CONTRACT, with a column that reads and writes would fail on.
 *
 * Pure in the sense that matters: it takes a {@link DSLContext} and works entirely
 * from the journal. No storage definitions, no Mongo, no override chains. Recovery
 * runs when the rest of the system is in a state nobody planned, so it depends on as
 * little of that system as it can.
 */
public final class MySQLMigrationSweeper {

    /**
     * How long a claim stands before another node may take it.
     *
     * {@code progress} refreshes the claim before every statement, so a migration
     * that is genuinely running renews this continuously and is never stolen. At the
     * measured data sizes a whole plan is seconds, so a minute is already generous;
     * the window only has to outlast the gap between two statements.
     */
    public static final int DEFAULT_STALE_AFTER_SECONDS = 60;

    private MySQLMigrationSweeper() {
    }

    /** Every tenant schema on this server that has ever run a migration. */
    public static Mono<List<String>> schemas(DSLContext ctx) {
        return Flux.from(ctx.resultQuery(MySQLMigrationJournal.schemasWithJournal()))
                .map(r -> String.valueOf(r.get(0)))
                .collectList();
    }

    /** Interrupted rows in one tenant, newest claim last so the stalest is taken first. */
    public static Mono<List<InterruptedMigration>> interrupted(DSLContext ctx, String db, int staleAfterSeconds) {

        return Flux.from(ctx.resultQuery(MySQLMigrationJournal.unfinished(db, staleAfterSeconds)))
                .map(r -> new InterruptedMigration(
                        db,
                        String.valueOf(r.get(0)),
                        String.valueOf(r.get(1)),
                        String.valueOf(r.get(2)),
                        String.valueOf(r.get(3)),
                        String.valueOf(r.get(4)),
                        intOf(r.get(5)),
                        intOf(r.get(6)),
                        intOf(r.get(7)),
                        intOf(r.get(8)),
                        MigrationPlanCodec.read(r.get(9) == null ? null : String.valueOf(r.get(9)))))
                .collectList();
    }

    /** One pass over every tenant on this connection. */
    public static Mono<RecoveryReport> sweep(DSLContext ctx) {
        return sweep(ctx, DEFAULT_STALE_AFTER_SECONDS);
    }

    public static Mono<RecoveryReport> sweep(DSLContext ctx, int staleAfterSeconds) {

        Map<String, MigrationOutcome> outcomes = new LinkedHashMap<>();
        List<String> unplayable = new ArrayList<>();
        int[] counts = new int[3]; // found, claimed, resumed

        return schemas(ctx)
                .flatMapMany(Flux::fromIterable)
                .concatMap(db -> interrupted(ctx, db, staleAfterSeconds))
                .flatMapIterable(list -> list)
                .concatMap(row -> {
                    counts[0]++;

                    if (!row.isReplayable()) {
                        unplayable.add(row.describe());
                        return Mono.empty();
                    }

                    return claim(ctx, row, staleAfterSeconds).flatMap(won -> {
                        // Lost the race. Another node is finishing it, which is the
                        // right outcome rather than a failure.
                        if (!Boolean.TRUE.equals(won)) return Mono.empty();

                        counts[1]++;

                        return MySQLMigrationRunner.resume(
                                        ctx, row.database(), row.id(), row.plan(), row.statementIndex(),
                                        row.appliedCount())
                                .doOnNext(outcome -> {
                                    outcomes.put(row.database() + "/" + row.storageName(), outcome);
                                    if (outcome.isSuccess()) counts[2]++;
                                });
                    });
                })
                .then(Mono.fromSupplier(
                        () -> new RecoveryReport(counts[0], counts[1], counts[2], outcomes, unplayable)));
    }

    /**
     * Exactly one node wins.
     *
     * The guard lives in the WHERE clause rather than in a read followed by a write.
     * Two nodes sweeping at the same instant is the normal case during a rolling
     * deploy, which is also exactly when interrupted rows exist, so a check-then-act
     * would race precisely when it is being relied on.
     */
    static Mono<Boolean> claim(DSLContext ctx, InterruptedMigration row, int staleAfterSeconds) {
        return Mono.from(ctx.query(MySQLMigrationJournal.claim(
                        row.database(), row.id(), MySQLMigrationRunner.NODE, staleAfterSeconds)))
                .map(updated -> updated == 1)
                .defaultIfEmpty(Boolean.FALSE);
    }

    private static int intOf(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }
}
