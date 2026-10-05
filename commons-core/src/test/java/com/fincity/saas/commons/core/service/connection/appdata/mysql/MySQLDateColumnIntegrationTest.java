package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.string.StringFormat;
import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.nocode.kirun.engine.json.schema.type.Type;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import reactor.core.publisher.Mono;

/**
 * A declared date written with an offset, through a real MySQL.
 *
 * This class used to claim its sessions ran at +05:30 "because MySQL follows the host
 * OS". That is true of a MySQL installed on a laptop and false of one in a container,
 * whose OS is UTC whatever the host is - and the assertion resting on it was simply
 * wrong. The zone is now asserted rather than assumed, so a server that is NOT UTC
 * fails loudly here instead of quietly changing what these tests mean.
 *
 * What they cover: an offset-bearing value is normalised by MySQL to the session
 * zone, so what comes back is the instant, not the wall clock that was typed. That
 * is the reason the codec returns the stored instant as ISO text rather than trying
 * to carry the original offset through.
 */
@Testcontainers
class MySQLDateColumnIntegrationTest extends AbstractMySQLIntegrationTest {


    private static final String DB = "dateapp";
    private static final String TABLE = "orders";

    private static final MySQLValueCodec CODEC = new MySQLValueCodec(new ObjectMapper());

    private static DSLContext ctx;

    private static Schema storageSchema() {
        return new Schema()
                .setType(Type.of(SchemaType.OBJECT))
                .setProperties(Map.of(
                        "placedAt", Schema.ofString("placedAt").setFormat(StringFormat.DATETIME),
                        "opensAt", Schema.ofString("opensAt").setFormat(StringFormat.TIME)));
    }

    private static Set<String> dates() {
        return MySQLTypeMapper.dateStringColumns(storageSchema());
    }

    @BeforeAll
    static void start() {
        ctx = mysql();
        schema("dateapp");
    }

    @BeforeEach
    void freshTable() {
        Mono.from(ctx.query("DROP TABLE IF EXISTS `" + DB + "`.`" + TABLE + "`")).block();
        Mono.from(ctx.query("USE `" + DB + "`; "
                        + MySQLTablePlanner.createTable(TABLE, MySQLTypeMapper.columns(storageSchema()))))
                .block();
    }

    private static void insert(String id, Map<String, Object> encoded) {
        Map<Field<?>, Object> values = new LinkedHashMap<>();
        encoded.forEach((k, v) -> values.put(DSL.field(DSL.name(k)), v));
        values.put(DSL.field(DSL.name(MySQLTypeMapper.ID_COLUMN)), id);
        Mono.from(ctx.insertInto(DSL.table(DSL.name(DB, TABLE))).set(values)).block();
    }

    private static Map<String, Object> read(String id) {
        return Mono.from(ctx.select()
                        .from(DSL.table(DSL.name(DB, TABLE)))
                        .where(DSL.field(DSL.name(MySQLTypeMapper.ID_COLUMN)).eq(id)))
                .map(r -> CODEC.decode(new LinkedHashMap<>(r.intoMap()), Set.of(), dates()))
                .block();
    }

    private static Map<String, Object> row(String placedAt, String opensAt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("placedAt", placedAt);
        m.put("opensAt", opensAt);
        return m;
    }

    @Test
    @DisplayName("an offset is stored as the UTC instant, whatever the session zone")
    void offsetStoredAsUtc() {
        insert("a", CODEC.encode(row("2026-01-15T10:00:00+05:30", "10:00:00+05:30"), Set.of(), dates()));

        Map<String, Object> back = read("a");
        assertEquals("2026-01-15T04:30:00", back.get("placedAt"));
        assertEquals("04:30:00", back.get("opensAt"));
    }

    @Test
    @DisplayName("a Z is UTC already, and a datetime keeps its milliseconds")
    void zuluWithMillis() {
        insert("b", CODEC.encode(row("2026-01-15T10:00:00.123Z", "10:00:00Z"), Set.of(), dates()));

        // Not milliseconds on the TIME column: MySQL stores them, but an untyped jOOQ
        // select hands TIME back as java.sql.Time, which has already dropped them.
        Map<String, Object> back = read("b");
        assertEquals("2026-01-15T10:00:00.123", back.get("placedAt"));
        assertEquals("10:00:00", back.get("opensAt"));
    }

    @Test
    @DisplayName("a value with no offset is stored as written")
    void noOffset() {
        insert("c", CODEC.encode(row("2026-01-20T10:00:00", "10:00:00"), Set.of(), dates()));

        assertEquals("2026-01-20T10:00:00", read("c").get("placedAt"));
    }

    @Test
    @DisplayName("the server really is UTC, which is what every expectation here rests on")
    void sessionIsUtc() {

        // Asserted rather than assumed. These tests encode exact wall times, so a
        // server on another zone would not fail with a timezone error - it would
        // quietly make every expectation below mean something different.
        Object now = Mono.from(ctx.resultQuery("SELECT NOW()")).map(r -> r.get(0)).block();
        Object utc = Mono.from(ctx.resultQuery("SELECT UTC_TIMESTAMP()")).map(r -> r.get(0)).block();

        assertEquals(String.valueOf(now).substring(0, 16), String.valueOf(utc).substring(0, 16));
    }

    @Test
    @DisplayName("an offset is normalised away, so the wall clock you typed is not what you get")
    void offsetIsNormalisedNotPreserved() {

        insert("d", CODEC.encode(row("2026-01-15T10:00:00+05:30", null), Set.of()));

        // 10:00 at +05:30 is 04:30 UTC, and the column holds no offset to put it back
        // with. This is why the codec hands back the stored instant as ISO text
        // instead of pretending the original offset survived: it did not, and a
        // caller that re-sent "10:00+05:30" would be writing a different moment each
        // time the server's zone changed.
        assertEquals("2026-01-15T04:30:00", read("d").get("placedAt"));
    }
}
