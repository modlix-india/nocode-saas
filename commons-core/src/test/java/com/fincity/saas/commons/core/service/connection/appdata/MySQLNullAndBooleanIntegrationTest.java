package com.fincity.saas.commons.core.service.connection.appdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.AbstractMySQLIntegrationTest;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.MySQLColumnNames;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.MySQLTablePlanner;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.MySQLTypeMapper;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.MySQLValueCodec;

import reactor.core.publisher.Mono;

/**
 * The update and read paths of the MySQL backend against a real MySQL 8.4: a null
 * becomes SQL NULL (PATCH merges, PUT replaces), a NOT NULL column refuses with a
 * message that names it, and a BOOLEAN field reads back as true / false.
 */
@Testcontainers
class MySQLNullAndBooleanIntegrationTest extends AbstractMySQLIntegrationTest {

    private static final String DB = "nullbool";
    private static final String TABLE = "project_t";
    private static final MySQLValueCodec CODEC = new MySQLValueCodec(new ObjectMapper());

    private static DSLContext ctx;
    private static Schema physical;

    @BeforeAll
    static void start() {
        ctx = mysql();
        schema(DB);
        physical = MySQLColumnNames.physical(MySQLNullAndBooleanTest.schema());
        exec("USE `" + DB + "`; " + MySQLTablePlanner.createTable(TABLE, MySQLTypeMapper.columns(physical)));
    }

    @BeforeEach
    void seed() {
        exec("DELETE FROM `" + DB + "`.`" + TABLE + "`");
        Map<Field<?>, Object> values = new LinkedHashMap<>();
        values.put(DSL.field(DSL.name("_id")), "426");
        values.put(DSL.field(DSL.name("name")), "Tower A");
        values.put(DSL.field(DSL.name("description")), "unlink-test");
        values.put(DSL.field(DSL.name("active")), true);
        values.put(DSL.field(DSL.name("optIn")), false);
        values.put(DSL.field(DSL.name("count")), 3);
        Mono.from(ctx.insertInto(DSL.table(DSL.name(DB, TABLE))).set(values)).block();
    }

    private static void exec(String sql) {
        Mono.from(ctx.query(sql)).block();
    }

    private static void update(Map<String, Object> data, Boolean override) {
        Mono.from(ctx.update(DSL.table(DSL.name(DB, TABLE)))
                        .set(MySQLAppDataService.updateValues(
                                CODEC, MySQLNullAndBooleanTest.storage(), physical, data, override))
                        .where(DSL.field(DSL.name("_id")).eq("426")))
                .block();
    }

    /** The row as the API hands it back. */
    private static Map<String, Object> read() {
        MySQLAppDataService.Decoding decoding = MySQLAppDataService.Decoding.of(physical, null);
        return Mono.from(ctx.select().from(DSL.table(DSL.name(DB, TABLE))).where(DSL.field(DSL.name("_id")).eq("426")))
                .map(r -> decoding.row(CODEC, new LinkedHashMap<>(r.intoMap()), physical))
                .block();
    }

    @Test
    @DisplayName("PATCH with a field set to null clears that column and keeps the rest")
    void patchNullClears() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("_id", "426");
        data.put("description", null);
        update(data, false);

        Map<String, Object> row = read();
        assertNull(row.get("description"), "" + row);
        assertEquals("Tower A", row.get("name"));
        assertEquals(3, ((Number) row.get("count")).intValue());
    }

    @Test
    @DisplayName("PUT replaces the row: a field sent as null and a field left out are both cleared")
    void putReplaces() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("_id", "426");
        data.put("name", "Tower B");
        data.put("description", null);
        data.put("active", false);
        update(data, true);

        Map<String, Object> row = read();
        assertEquals("Tower B", row.get("name"));
        assertNull(row.get("description"));
        assertNull(row.get("count"), "left out of a PUT, so cleared: " + row);
        assertNull(row.get("optIn"));
        assertEquals(Boolean.FALSE, row.get("active"));
    }

    @Test
    @DisplayName("BOOLEAN columns read back as true / false, and a NULL one as null")
    void booleansReadBack() {
        Map<String, Object> row = read();
        assertEquals(Boolean.TRUE, row.get("active"), "" + row);
        assertEquals(Boolean.FALSE, row.get("optIn"));
        assertTrue(row.containsKey("IFSC Flag"));
        assertNull(row.get("IFSC Flag"));
        // A non-boolean integer is left a number.
        assertTrue(row.get("count") instanceof Number);
    }

    @Test
    @DisplayName("MySQL refusing a NULL on a NOT NULL column is recognised, with the column named")
    void notNullIsNamed() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("_id", "426");
        data.put("name", null);

        RuntimeException e = assertThrows(RuntimeException.class, () -> update(data, false));
        assertEquals("name", MySQLAppDataService.nullColumn(e), "unrecognised: " + e);

        // And the row is untouched.
        assertNotNull(read().get("name"));
    }
}
