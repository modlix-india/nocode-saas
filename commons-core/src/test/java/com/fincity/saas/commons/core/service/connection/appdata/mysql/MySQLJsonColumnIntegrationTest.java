package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
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
import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.nocode.kirun.engine.json.schema.type.Type;
import com.fincity.saas.commons.model.condition.FilterCondition;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A nested field written and read back through a real MySQL.
 *
 * The codec and the type mapper are both covered without a database. What they cannot
 * show is whether MySQL accepts what the codec produces and returns what the codec
 * expects, and every one of these assertions is a place where a correct-looking
 * encoding is rejected or silently reshaped by the engine.
 */
@Testcontainers
class MySQLJsonColumnIntegrationTest extends AbstractMySQLIntegrationTest {


    private static final String DB = "jsonapp";
    private static final String TABLE = "people";

    private static final MySQLValueCodec CODEC = new MySQLValueCodec(new ObjectMapper());

    private static DSLContext ctx;

    /** name, a nested address, and a list of tags: one of each kind of column. */
    private static Schema storageSchema() {
        return new Schema()
                .setType(Type.of(SchemaType.OBJECT))
                .setProperties(Map.of(
                        "name", Schema.ofString("name").setMaxLength(60),
                        "address",
                                new Schema()
                                        .setType(Type.of(SchemaType.OBJECT))
                                        .setProperties(Map.of(
                                                "city", Schema.ofString("city").setMaxLength(60),
                                                "floor", Schema.ofInteger("floor"))),
                        "tags", Schema.ofArray("tags", Schema.ofString("tag"))));
    }

    private static Set<String> json() {
        return MySQLTypeMapper.jsonColumns(storageSchema());
    }

    @BeforeAll
    static void start() {
        ctx = mysql();
        schema("jsonapp");
    }

    @BeforeEach
    void freshTable() {
        Mono.from(ctx.query("DROP TABLE IF EXISTS `" + DB + "`.`" + TABLE + "`")).block();
        Mono.from(ctx.query("USE `" + DB + "`; "
                        + MySQLTablePlanner.createTable(TABLE, MySQLTypeMapper.columns(storageSchema()))))
                .block();
    }

    private static void insert(String id, String name, Object address, Object tags) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(MySQLTypeMapper.ID_COLUMN, id);
        row.put("name", name);
        row.put("address", address);
        row.put("tags", tags);

        Map<Field<?>, Object> values = new LinkedHashMap<>();
        CODEC.encode(row, json()).forEach((k, v) -> values.put(DSL.field(DSL.name(k)), v));

        Mono.from(ctx.insertInto(DSL.table(DSL.name(DB, TABLE))).set(values)).block();
    }

    private static List<Map<String, Object>> select(FilterCondition fc) {
        return Flux.from(ctx.select()
                        .from(DSL.table(DSL.name(DB, TABLE)))
                        .where(fc == null ? DSL.noCondition() : MySQLFilterBuilder.build(fc, json())))
                .map(r -> CODEC.decode(new LinkedHashMap<>(r.intoMap()), json()))
                .collectList()
                .block();
    }

    @Test
    @DisplayName("the generated table really does have JSON columns")
    void columnTypes() {
        Map<String, String> types = Flux.from(ctx.resultQuery(
                        "SELECT COLUMN_NAME, DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='" + DB
                                + "' AND TABLE_NAME='" + TABLE + "'"))
                .collectMap(r -> String.valueOf(r.get(0)), r -> String.valueOf(r.get(1)))
                .block();

        assertEquals("varchar", types.get("name"));
        assertEquals("json", types.get("address"));
        assertEquals("json", types.get("tags"));
        assertEquals("char", types.get("_id"));
    }

    @Test
    @DisplayName("a nested object survives the round trip unchanged")
    void objectRoundTrip() {
        insert("01A00000000000000000000001", "Asha", Map.of("city", "Pune", "floor", 3), List.of("a", "b"));

        Map<String, Object> back = select(null).get(0);

        assertInstanceOf(Map.class, back.get("address"));
        assertEquals("Pune", ((Map<?, ?>) back.get("address")).get("city"));

        // MySQL stores JSON in its own binary form and reparses it on the way out, so
        // a number is a place the engine itself could change the value.
        assertEquals(3, ((Map<?, ?>) back.get("address")).get("floor"));
        assertEquals(List.of("a", "b"), back.get("tags"));
    }

    @Test
    @DisplayName("a null nested field reads back as null, not as an empty object")
    void nullStays() {
        insert("01A00000000000000000000002", "Bo", null, null);

        Map<String, Object> back = select(null).get(0);

        assertEquals(null, back.get("address"));
        assertEquals(null, back.get("tags"));
    }

    @Test
    @DisplayName("writing a nested value without encoding it is rejected by MySQL")
    void unencodedIsRejected() {
        // This is why the codec exists rather than being a convenience. `Pune` is not
        // valid JSON, so without encoding the write does not silently misbehave - it
        // fails outright, on every nested field, on every storage.
        assertThrows(
                Exception.class,
                () -> Mono.from(ctx.query("INSERT INTO `" + DB + "`.`" + TABLE
                                + "` (`_id`, `address`) VALUES ('01A00000000000000000000003', 'Pune')"))
                        .block());
    }

    @Test
    @DisplayName("a filter reads a path inside the nested object")
    void filterByPath() {
        insert("01A00000000000000000000004", "Asha", Map.of("city", "Pune"), null);
        insert("01A00000000000000000000005", "Bo", Map.of("city", "Mumbai"), null);

        List<Map<String, Object>> found = select(FilterCondition.make("address.city", "Pune"));

        assertEquals(1, found.size(), "the path filter has to actually select, not match everything or nothing");
        assertEquals("Asha", found.get(0).get("name"));
    }

    @Test
    @DisplayName("the unquote is what makes LIKE on a path work at all")
    void unquoteMatters() {
        insert("01A00000000000000000000006", "Asha", Map.of("city", "Pune"), null);

        // JSON_EXTRACT alone returns `"Pune"`, quotes included, six characters not
        // four. Equality survives that because MySQL coerces, which is exactly what
        // makes this worth asserting: the operator most people would test with hides
        // the problem, and LIKE is where it surfaces. An anchored pattern matches the
        // quote instead of the letter and returns nothing, and a filter that silently
        // matches nothing reads just like one that correctly matched nothing.
        long quoted = Mono.from(ctx.resultQuery("SELECT COUNT(*) FROM `" + DB + "`.`" + TABLE
                        + "` WHERE JSON_EXTRACT(`address`, '$.city') LIKE 'Pun%'"))
                .map(r -> ((Number) r.get(0)).longValue())
                .block();

        assertEquals(0, quoted, "without the unquote an anchored LIKE matches the quote, not the letter");

        FilterCondition like = FilterCondition.make("address.city", "Pun%")
                .setOperator(com.fincity.saas.commons.model.condition.FilterConditionOperator.LIKE);

        assertEquals(1, select(like).size());
    }

    @Test
    @DisplayName("a number inside a nested object compares as a number")
    void numericPath() {
        insert("01A00000000000000000000013", "Asha", Map.of("city", "Pune", "floor", 12), null);
        insert("01A00000000000000000000014", "Bo", Map.of("city", "Pune", "floor", 3), null);

        FilterCondition gt = FilterCondition.make("address.floor", 9)
                .setOperator(com.fincity.saas.commons.model.condition.FilterConditionOperator.GREATER_THAN);

        // The extracted value is text, so this leans on MySQL coercing it back to a
        // number. If it compared as text, 3 would sort after 12 and this would return
        // both rows - which is the precision the note on a nested column warns about,
        // and the reason to keep anything you filter on seriously at the top level.
        assertEquals(1, select(gt).size());
        assertEquals("Asha", select(gt).get(0).get("name"));
    }

    @Test
    @DisplayName("a path into a field that is absent on some rows simply does not match them")
    void missingPath() {
        insert("01A00000000000000000000007", "Asha", Map.of("city", "Pune"), null);
        insert("01A00000000000000000000008", "Bo", Map.of("pin", "400001"), null);

        assertEquals(1, select(FilterCondition.make("address.city", "Pune")).size());
    }

    @Test
    @DisplayName("deeply nested data goes in whole, however deep it is")
    void deeplyNested() {
        Map<String, Object> deep = Map.of(
                "city", "Pune", "geo", Map.of("lat", 18.5, "lng", 73.8, "box", List.of(Map.of("x", 1))));

        insert("01A00000000000000000000009", "Asha", deep, null);

        Map<String, Object> back = (Map<String, Object>) select(null).get(0).get("address");

        // No amount of depth needs DDL, which is the whole argument for the JSON
        // column: the nested shape is itself overridable per client, and flattening it
        // would turn one edit to a shared sub-schema into an ALTER on every tenant.
        assertNotNull(back);
        assertEquals(deep, back);
    }

    @Test
    @DisplayName("a path filter composes with an ordinary column filter")
    void mixedFilter() {
        insert("01A00000000000000000000010", "Asha", Map.of("city", "Pune"), null);
        insert("01A00000000000000000000011", "Asha", Map.of("city", "Mumbai"), null);
        insert("01A00000000000000000000012", "Bo", Map.of("city", "Pune"), null);

        List<Map<String, Object>> found = Flux.from(ctx.select()
                        .from(DSL.table(DSL.name(DB, TABLE)))
                        .where(MySQLFilterBuilder.build(
                                com.fincity.saas.commons.model.condition.ComplexCondition.and(
                                        FilterCondition.make("name", "Asha"),
                                        FilterCondition.make("address.city", "Pune")),
                                json())))
                .map(r -> CODEC.decode(new LinkedHashMap<>(r.intoMap()), json()))
                .collectList()
                .block();

        assertEquals(1, found.size());
        assertTrue(found.get(0).get("_id").toString().endsWith("10"));
    }
}
