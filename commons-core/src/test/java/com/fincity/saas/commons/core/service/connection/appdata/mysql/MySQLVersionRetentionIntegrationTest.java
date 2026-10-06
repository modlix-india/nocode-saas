package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fincity.saas.commons.core.service.connection.appdata.VersionRetention;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.jooq.Table;
import org.jooq.impl.DSL;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Version history actually gets trimmed, against a real MySQL.
 *
 * Before this existed nothing ever removed a version row: every update to an
 * audited or versioned storage added history that lived forever, and on a
 * frequently written row the history outgrows the data it describes.
 *
 * Drives {@link MySQLVersionTrim} - the same class the write path calls - rather
 * than a copy of the statements, because a test holding its own copy proves only
 * that the copy works.
 */
class MySQLVersionRetentionIntegrationTest extends AbstractMySQLIntegrationTest {

    private static final String DB = "retention_db";
    private static final String TABLE = "thing_version";

    @BeforeAll
    static void setUp() {
        schema(DB);
        Mono.from(mysql().query("DROP TABLE IF EXISTS `" + DB + "`.`" + TABLE + "`")).block();
        Mono.from(mysql().query(MySQLVersionTable.createTable(DB, "thing"))).block();
    }

    private static Table<?> table() {
        return DSL.table(DSL.name(DB, MySQLVersionTable.nameFor("thing")));
    }

    private static void wipe(String objectId) {
        Mono.from(mysql().deleteFrom(table())
                        .where(DSL.field(DSL.name(MySQLVersionTable.OBJECT_ID)).eq(objectId)))
                .block();
    }

    /** One history row, at a chosen age, so both bounds can be aimed precisely. */
    private static void version(String objectId, int daysOld) {
        Mono.from(mysql().insertInto(table())
                        .set(DSL.field(DSL.name(MySQLTypeMapper.ID_COLUMN)), com.fincity.saas.commons.util.UniqueUtil.ulid())
                        .set(DSL.field(DSL.name(MySQLVersionTable.OBJECT_ID)), objectId)
                        .set(DSL.field(DSL.name(MySQLVersionTable.OPERATION)), "UPDATE")
                        .set(
                                DSL.field(DSL.name(MySQLVersionTable.CREATED_AT)),
                                LocalDateTime.now(ZoneOffset.UTC).minusDays(daysOld).minusSeconds(daysOld)))
                .block();
    }

    private static int count(String objectId) {
        return Mono.from(mysql().selectCount()
                        .from(table())
                        .where(DSL.field(DSL.name(MySQLVersionTable.OBJECT_ID)).eq(objectId)))
                .map(r -> r.get(0, Integer.class))
                .block();
    }

    @Test
    @DisplayName("History older than the age bound is removed")
    void trimsByAge() {

        String id = "age";
        wipe(id);
        for (int d : List.of(200, 150, 100, 10, 1)) version(id, d);
        assertEquals(5, count(id));

        MySQLVersionTrim.trim(mysql(), table(), id, new VersionRetention(90, 0)).block();

        assertEquals(2, count(id), "only the two rows newer than 90 days should remain");
    }

    @Test
    @DisplayName("History beyond the count bound is removed, newest kept")
    void trimsByCount() {

        String id = "count";
        wipe(id);
        for (int d : List.of(50, 40, 30, 20, 10)) version(id, d);
        assertEquals(5, count(id));

        MySQLVersionTrim.trim(mysql(), table(), id, new VersionRetention(0, 2)).block();

        assertEquals(2, count(id));

        List<LocalDateTime> left = Flux.from(mysql().select(
                                DSL.field(DSL.name(MySQLVersionTable.CREATED_AT), LocalDateTime.class))
                        .from(table())
                        .where(DSL.field(DSL.name(MySQLVersionTable.OBJECT_ID)).eq(id)))
                .map(r -> r.get(0, LocalDateTime.class))
                .collectList()
                .block();

        LocalDateTime twentyDaysAgo = LocalDateTime.now(ZoneOffset.UTC).minusDays(21);
        assertTrue(
                left.stream().allMatch(t -> t.isAfter(twentyDaysAgo)),
                "the survivors must be the NEWEST two, not an arbitrary two");
    }

    @Test
    @DisplayName("Both bounds apply together: a row must satisfy each to survive")
    void bothBoundsApply() {

        String id = "both";
        wipe(id);
        // Four recent enough for the age bound, but only two may survive the count.
        for (int d : List.of(300, 5, 4, 3, 2)) version(id, d);

        MySQLVersionTrim.trim(mysql(), table(), id, new VersionRetention(90, 2)).block();

        assertEquals(2, count(id), "age removes the 300-day row, count removes all but the newest two");
    }

    @Test
    @DisplayName("A zeroed policy keeps everything, so history can still be opted into forever")
    void keepsEverythingWhenUnlimited() {

        String id = "unlimited";
        wipe(id);
        for (int d : List.of(500, 400, 300)) version(id, d);

        MySQLVersionTrim.trim(mysql(), table(), id, new VersionRetention(0, 0)).block();

        assertEquals(3, count(id));
    }

    @Test
    @DisplayName("Trimming one row's history never touches another's")
    void isScopedToOneObject() {

        wipe("mine");
        wipe("yours");
        for (int d : List.of(300, 200, 100)) version("mine", d);
        for (int d : List.of(300, 200, 100)) version("yours", d);

        MySQLVersionTrim.trim(mysql(), table(), "mine", new VersionRetention(90, 0)).block();

        assertEquals(0, count("mine"));
        assertEquals(3, count("yours"), "another object's history must be untouched");
    }

    @Test
    @DisplayName("Fewer versions than the bound is a no-op, not an error")
    void toleratesFewerThanTheBound() {

        String id = "few";
        wipe(id);
        version(id, 1);

        MySQLVersionTrim.trim(mysql(), table(), id, new VersionRetention(90, 50)).block();

        assertEquals(1, count(id));
    }
}
