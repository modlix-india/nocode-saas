package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.nocode.kirun.engine.json.schema.type.Type;
import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.document.Storage.StorageIndex;
import com.fincity.saas.commons.core.document.Storage.StorageIndexField;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Field names as an outside system sends them, against a real MySQL.
 *
 * The HDFC collection webhook posts "IFSC Code", "Virtual Account " and the like, and
 * the storages that keep those payloads were written to match. Each step below is the
 * one the backend takes, in the same order, so that a field name with a space is
 * shown to survive DDL, indexes, a write, a filter, a sort and the read back.
 */
@Testcontainers
class MySQLReceivedFieldNamesIntegrationTest extends AbstractMySQLIntegrationTest {

    private static final String DB = "received";
    private static final String TABLE = "hdfc";

    private static DSLContext ctx;
    private static Schema physical;

    @BeforeAll
    static void start() {
        ctx = mysql();
        schema(DB);

        physical = MySQLColumnNames.physical(fields());

        exec("USE `" + DB + "`; " + MySQLTablePlanner.createTable(TABLE, MySQLTypeMapper.columns(physical)));

        Set<String> columns = new java.util.LinkedHashSet<>();
        MySQLTypeMapper.columns(physical).forEach(c -> columns.add(c.name()));
        for (MySQLIndexes.Index index : MySQLIndexes.desired(storage(), columns)) exec(index.ddl(DB, TABLE));

        insert("HDFC0000001", "VA-1", 500.0, "Asha", "{\"city\": \"Pune\"}");
        insert("HDFC0000002", "VA-2", 150.0, "Ravi", "{\"city\": \"Hyderabad\"}");
    }

    private static Schema fields() {
        Map<String, Schema> props = new LinkedHashMap<>();
        props.put("IFSC Code", Schema.ofString("IFSC Code").setMaxLength(20));
        props.put("Virtual Account ", Schema.ofString("Virtual Account ").setMaxLength(40));
        props.put("Amount", Schema.ofDouble("Amount"));
        props.put("Remitter Name", Schema.ofString("Remitter Name").setMaxLength(100));
        props.put("Bank Details", new Schema().setType(Type.of(SchemaType.OBJECT)));
        return new Schema().setType(Type.of(SchemaType.OBJECT)).setProperties(props).setRequired(List.of("IFSC Code"));
    }

    private static Storage storage() {
        StorageIndex byIfsc = new StorageIndex();
        byIfsc.setUnique(true);
        byIfsc.setFields(List.of(new StorageIndexField().setFieldName("IFSC Code")));

        Storage s = new Storage();
        s.setName("hdfc").setAppCode("cxapp").setClientCode("SYSTEM").setVersion(1);
        s.setUniqueName(TABLE);
        s.setIndexes(Map.of("byIfsc", byIfsc));
        s.setTextIndexFields(List.of("Remitter Name"));
        return s;
    }

    private static void exec(String sql) {
        Mono.from(ctx.query(sql)).block();
    }

    /** Written by field name, as a caller would, and translated as create does. */
    private static void insert(String ifsc, String va, double amount, String remitter, String bank) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("_id", ifsc);
        row.put("IFSC Code", ifsc);
        row.put("Virtual Account ", va);
        row.put("Amount", amount);
        row.put("Remitter Name", remitter);
        row.put("Bank Details", bank);

        Map<Field<?>, Object> values = new LinkedHashMap<>();
        MySQLColumnNames.toColumns(row).forEach((k, v) -> values.put(DSL.field(DSL.name(k)), v));
        Mono.from(ctx.insertInto(DSL.table(DSL.name(DB, TABLE))).set(values)).block();
    }

    private static List<Map<String, Object>> read(FilterCondition filter, String sortBy) {
        MySQLFieldResolver resolver = MySQLFieldResolver.of(MySQLTypeMapper.jsonColumns(physical));
        return Flux.from(ctx.select()
                        .from(DSL.table(DSL.name(DB, TABLE)))
                        .where(filter == null ? DSL.noCondition() : MySQLFilterBuilder.build(filter, resolver))
                        .orderBy(resolver.resolve(sortBy).desc()))
                .map(r -> MySQLColumnNames.toFields(new LinkedHashMap<>(r.intoMap()), physical))
                .collectList()
                .block();
    }

    @Test
    @DisplayName("the table and its indexes are built on normalised columns")
    void ddl() {
        List<String> columns = Flux.from(ctx.resultQuery(
                        "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = '" + DB
                                + "' AND TABLE_NAME = '" + TABLE + "'"))
                .map(r -> String.valueOf(r.get(0)))
                .collectList()
                .block();

        assertTrue(columns.containsAll(List.of("IFSC_Code", "Virtual_Account_", "Amount", "Remitter_Name")), "" + columns);

        List<String> indexed = Flux.from(ctx.resultQuery(
                        "SELECT DISTINCT COLUMN_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = '" + DB
                                + "' AND TABLE_NAME = '" + TABLE + "'"))
                .map(r -> String.valueOf(r.get(0)))
                .collectList()
                .block();

        assertTrue(indexed.containsAll(List.of("IFSC_Code", "Remitter_Name")), "" + indexed);
    }

    @Test
    @DisplayName("a filter and a sort by the field name reach the column, and rows come back by field")
    void filterSortAndRead() {
        List<Map<String, Object>> rows = read(
                new FilterCondition()
                        .setField("IFSC Code")
                        .setOperator(FilterConditionOperator.EQUALS)
                        .setValue("HDFC0000002"),
                "Amount");

        assertEquals(1, rows.size());
        assertEquals("VA-2", rows.getFirst().get("Virtual Account "));
        assertEquals("Ravi", rows.getFirst().get("Remitter Name"));

        List<Map<String, Object>> all = read(null, "Amount");
        assertEquals("HDFC0000001", all.getFirst().get("IFSC Code"));
    }

    @Test
    @DisplayName("a path into a JSON field with a spaced name works too")
    void jsonPath() {
        List<Map<String, Object>> rows = read(
                new FilterCondition()
                        .setField("Bank Details.city")
                        .setOperator(FilterConditionOperator.EQUALS)
                        .setValue("Pune"),
                "Amount");

        assertEquals(1, rows.size());
        assertEquals("HDFC0000001", rows.getFirst().get("IFSC Code"));
    }
}
