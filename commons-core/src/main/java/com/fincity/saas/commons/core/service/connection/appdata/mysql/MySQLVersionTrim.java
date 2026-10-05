package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import com.fincity.saas.commons.core.service.connection.appdata.VersionRetention;
import java.time.LocalDateTime;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import reactor.core.publisher.Mono;

/**
 * Keeps one row's version history inside its retention policy.
 *
 * A class rather than a method on the service so the integration test drives the
 * SAME code the write path does. A test holding its own copy of the statements
 * proves only that the copy works.
 *
 * Trimmed per OBJECT, never as a sweep: both statements are driven by
 * idx_object_history (objectId, createdAt) and touch only the history of the row
 * just written, which is what makes this affordable inline on a write.
 */
public final class MySQLVersionTrim {

    private MySQLVersionTrim() {}

    private static Field<Object> objectId() {
        return DSL.field(DSL.name(MySQLVersionTable.OBJECT_ID));
    }

    private static Field<LocalDateTime> createdAt() {
        return DSL.field(DSL.name(MySQLVersionTable.CREATED_AT), LocalDateTime.class);
    }

    /**
     * Apply both bounds. A version row survives only by satisfying BOTH.
     *
     * The count bound takes two statements because MySQL will not accept a LIMIT
     * inside an IN subquery: read the timestamp of the Nth newest, then delete
     * everything strictly older. Rows sharing that exact timestamp survive, so a
     * burst of writes inside one second can leave slightly more than N. That is the
     * right way to be wrong - the alternative drops versions that are arguably
     * still among the most recent.
     */
    public static Mono<Boolean> trim(
            DSLContext ctx, Table<?> table, String id, VersionRetention retention) {

        if (ctx == null || table == null || id == null || retention == null || retention.keepsEverything())
            return Mono.just(Boolean.TRUE);

        return byAge(ctx, table, id, retention).then(byCount(ctx, table, id, retention));
    }

    public static Mono<Boolean> byAge(
            DSLContext ctx, Table<?> table, String id, VersionRetention retention) {

        if (!retention.trimsByAge()) return Mono.just(Boolean.TRUE);

        return Mono.from(ctx.deleteFrom(table)
                        .where(objectId().eq(id))
                        .and(createdAt().lt(retention.cutoff())))
                .thenReturn(Boolean.TRUE)
                .defaultIfEmpty(Boolean.TRUE);
    }

    public static Mono<Boolean> byCount(
            DSLContext ctx, Table<?> table, String id, VersionRetention retention) {

        if (!retention.trimsByCount()) return Mono.just(Boolean.TRUE);

        return Mono.from(ctx.select(createdAt())
                        .from(table)
                        .where(objectId().eq(id))
                        .orderBy(createdAt().desc())
                        .limit(1)
                        .offset(retention.count() - 1))
                .map(r -> r.get(createdAt()))
                .flatMap(oldestKept -> Mono.from(ctx.deleteFrom(table)
                                .where(objectId().eq(id))
                                .and(createdAt().lt(oldestKept)))
                        .thenReturn(Boolean.TRUE))
                .defaultIfEmpty(Boolean.TRUE);
    }
}
