package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.string.StringFormat;
import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.nocode.kirun.engine.json.schema.type.Type;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.ComplexConditionOperator;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.util.UniqueUtil;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The generated SQL, executed against a real MySQL.
 *
 * Everything else about this backend is unit tested, including the SQL as rendered
 * text. Rendering correctly is not evidence that MySQL accepts it: a wrong type name,
 * a reserved word used unquoted or a malformed CREATE only shows up here.
 */
@Testcontainers
class MySQLGeneratedSqlIntegrationTest extends AbstractMySQLIntegrationTest {


    private static DSLContext ctx;

    private static final String TABLE = "sales";

    @BeforeAll
    static void start() {
        ctx = mysql();
        schema("appdata");

        // One table, built from a storage schema exactly as the backend would.
        exec("USE `appdata`; " + MySQLTablePlanner.createTable(TABLE, MySQLTypeMapper.columns(storageSchema())));
        seed();
    }

    private static Schema storageSchema() {
        Map<String, Schema> props = new LinkedHashMap<>();
        props.put("region", Schema.ofString("region").setMaxLength(40));
        props.put("channel", Schema.ofString("channel").setMaxLength(40));
        props.put("amount", Schema.ofDouble("amount"));
        props.put("quantity", Schema.ofInteger("quantity"));
        props.put("active", Schema.ofBoolean("active"));
        props.put("notes", Schema.ofString("notes"));
        props.put("placedAt", Schema.ofString("placedAt").setFormat(StringFormat.DATETIME));
        props.put("payload", new Schema().setType(Type.of(SchemaType.OBJECT)));
        return new Schema().setType(Type.of(SchemaType.OBJECT)).setProperties(props);
    }

    private static void exec(String sql) {
        Mono.from(ctx.query(sql)).block();
    }

    private static void insert(String region, String channel, double amount, int qty, boolean active) {
        Map<Field<?>, Object> v = new LinkedHashMap<>();
        v.put(DSL.field(DSL.name("_id")), UniqueUtil.ulid());
        v.put(DSL.field(DSL.name("region")), region);
        v.put(DSL.field(DSL.name("channel")), channel);
        v.put(DSL.field(DSL.name("amount")), amount);
        v.put(DSL.field(DSL.name("quantity")), qty);
        v.put(DSL.field(DSL.name("active")), active);
        Mono.from(ctx.insertInto(DSL.table(DSL.name(TABLE))).set(v)).block();
    }

    private static void seed() {
        insert("North", "Web", 100.0, 1, true);
        insert("North", "Retail", 250.0, 2, true);
        insert("South", "Web", 70.0, 3, false);
        insert("East", "Partner", 410.0, 4, true);
    }

    private static List<Map<String, Object>> where(com.fincity.saas.commons.model.condition.AbstractCondition c) {
        return Flux.from(ctx.select()
                        .from(DSL.table(DSL.name(TABLE)))
                        .where(MySQLFilterBuilder.build(c)))
                .map(r -> (Map<String, Object>) new LinkedHashMap<>(r.intoMap()))
                .collectList()
                .block();
    }

    private static FilterCondition fc(String f, FilterConditionOperator op, Object v) {
        return new FilterCondition().setField(f).setOperator(op).setValue(v);
    }

    @Nested
    @DisplayName("the generated CREATE TABLE is accepted by MySQL")
    class Ddl {

        @Test
        @DisplayName("every mapped type is a valid column type")
        void tableExistsWithEveryColumn() {
            List<Map<String, Object>> cols = Flux.from(ctx.resultQuery(
                            "SELECT COLUMN_NAME, DATA_TYPE FROM information_schema.COLUMNS"
                                    + " WHERE TABLE_SCHEMA = 'appdata' AND TABLE_NAME = '" + TABLE + "'"))
                    .map(r -> (Map<String, Object>) new LinkedHashMap<>(r.intoMap()))
                    .collectList()
                    .block();

            assertNotNull(cols);
            List<String> names = cols.stream()
                    .map(c -> String.valueOf(c.get("COLUMN_NAME")))
                    .toList();

            assertTrue(names.contains("_id"));
            assertTrue(names.containsAll(
                    List.of("region", "channel", "amount", "quantity", "active", "notes", "placedAt", "payload")));
        }

        @Test
        @DisplayName("a declared date really is a DATETIME, not a number")
        void dateIsARealDateColumn() {
            String type = Mono.from(ctx.resultQuery(
                            "SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='appdata'"
                                    + " AND TABLE_NAME='" + TABLE + "' AND COLUMN_NAME='placedAt'"))
                    .map(r -> String.valueOf(r.get(0)))
                    .block();
            assertEquals("datetime", type);
        }

        @Test
        @DisplayName("a string with no maxLength really is TEXT")
        void unboundedStringIsText() {
            String type = Mono.from(ctx.resultQuery(
                            "SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='appdata'"
                                    + " AND TABLE_NAME='" + TABLE + "' AND COLUMN_NAME='notes'"))
                    .map(r -> String.valueOf(r.get(0)))
                    .block();
            assertEquals("text", type);
        }

        @Test
        @DisplayName("the ULID primary key holds a real ULID")
        void ulidFitsThePrimaryKey() {
            String id = Mono.from(ctx.resultQuery("SELECT `_id` FROM `" + TABLE + "` LIMIT 1"))
                    .map(r -> String.valueOf(r.get(0)))
                    .block();
            assertNotNull(id);
            assertEquals(26, id.length(), "CHAR(26) must not be truncating the id");
        }

        @Test
        @DisplayName("re-running CREATE is a no-op, which is what makes resume the recovery path")
        void createIsIdempotent() {
            exec("USE `appdata`; " + MySQLTablePlanner.createTable(TABLE, MySQLTypeMapper.columns(storageSchema())));
        }
    }

    @Nested
    @DisplayName("the generated WHERE clauses select the right rows")
    class Filters {

        @Test
        void equals() {
            assertEquals(2, where(fc("region", FilterConditionOperator.EQUALS, "North")).size());
        }

        @Test
        void comparison() {
            assertEquals(2, where(fc("amount", FilterConditionOperator.GREATER_THAN, 200)).size());
            assertEquals(1, where(fc("amount", FilterConditionOperator.LESS_THAN, 100)).size());
        }

        @Test
        void between() {
            FilterCondition c = fc("amount", FilterConditionOperator.BETWEEN, 100);
            c.setToValue(300);
            assertEquals(2, where(c).size());
        }

        @Test
        void inWithCommaSeparatedValue() {
            assertEquals(3, where(fc("region", FilterConditionOperator.IN, "North,South")).size());
        }

        @Test
        void looseEqual() {
            assertEquals(2, where(fc("channel", FilterConditionOperator.STRING_LOOSE_EQUAL, "eb")).size());
        }

        @Test
        void booleanPredicates() {
            assertEquals(3, where(fc("active", FilterConditionOperator.IS_TRUE, null)).size());
            assertEquals(1, where(fc("active", FilterConditionOperator.IS_FALSE, null)).size());
        }

        @Test
        @DisplayName("IS_NULL finds the rows never given a value")
        void isNull() {
            assertEquals(4, where(fc("notes", FilterConditionOperator.IS_NULL, null)).size());
        }

        @Test
        void negationExcludes() {
            FilterCondition c = fc("region", FilterConditionOperator.EQUALS, "North");
            c.setNegate(true);
            assertEquals(2, where(c).size());
        }

        @Test
        @DisplayName("an AND group narrows")
        void andGroup() {
            ComplexCondition cc = new ComplexCondition()
                    .setOperator(ComplexConditionOperator.AND)
                    .setConditions(List.of(
                            fc("region", FilterConditionOperator.EQUALS, "North"),
                            fc("channel", FilterConditionOperator.EQUALS, "Web")));
            assertEquals(1, where(cc).size());
        }

        @Test
        @DisplayName("a negated AND really behaves as the OR of negations, in SQL not just on paper")
        void deMorganHoldsInTheDatabase() {
            ComplexCondition cc = new ComplexCondition()
                    .setOperator(ComplexConditionOperator.AND)
                    .setConditions(List.of(
                            fc("region", FilterConditionOperator.EQUALS, "North"),
                            fc("channel", FilterConditionOperator.EQUALS, "Web")));
            cc.setNegate(true);

            // Everything except the single North+Web row.
            assertEquals(3, where(cc).size());
        }
    }
}
