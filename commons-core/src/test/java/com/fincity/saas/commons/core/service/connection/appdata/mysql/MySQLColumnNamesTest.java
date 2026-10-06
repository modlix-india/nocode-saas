package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jooq.conf.ParamType;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.commons.core.model.StorageColumnDefinition;
import com.fincity.saas.commons.model.JoinType;

/**
 * A field called "IFSC Code" is stored in the column IFSC_Code, and nobody outside
 * the MySQL backend should be able to tell.
 */
class MySQLColumnNamesTest {

    private static Schema hdfc() {
        Map<String, Schema> props = new LinkedHashMap<>();
        props.put("IFSC Code", Schema.ofString("IFSC Code"));
        props.put("Virtual Account ", Schema.ofString("Virtual Account "));
        props.put("paymentId", Schema.ofString("paymentId"));
        return Schema.ofObject("t").setProperties(props).setRequired(List.of("IFSC Code"));
    }

    @Test
    @DisplayName("the table is built from columns, keeping each field's type and requiredness")
    void ddl() {
        Schema physical = MySQLColumnNames.physical(hdfc());

        assertEquals(Set.of("IFSC_Code", "Virtual_Account_", "paymentId"), physical.getProperties().keySet());
        assertEquals(List.of("IFSC_Code"), physical.getRequired());

        String ddl = MySQLTablePlanner.createTable("t", MySQLTypeMapper.columns(physical));
        assertTrue(ddl.contains("`IFSC_Code` "), ddl);
        assertTrue(ddl.contains("`Virtual_Account_` "), ddl);
        assertTrue(ddl.contains("NOT NULL"), ddl);
    }

    @Test
    @DisplayName("a row goes in by column and comes back by field")
    void roundTrip() {
        Schema physical = MySQLColumnNames.physical(hdfc());

        Map<String, Object> in = new LinkedHashMap<>();
        in.put("IFSC Code", "HDFC0000001");
        in.put("Virtual Account ", "VA1");
        in.put("paymentId", "p1");

        Map<String, Object> stored = MySQLColumnNames.toColumns(in);
        assertEquals(Set.of("IFSC_Code", "Virtual_Account_", "paymentId"), stored.keySet());

        stored.put("_id", "01J");
        Map<String, Object> out = MySQLColumnNames.toFields(stored, physical);
        assertEquals("HDFC0000001", out.get("IFSC Code"));
        assertEquals("VA1", out.get("Virtual Account "));
        assertEquals("p1", out.get("paymentId"));
        assertEquals("01J", out.get("_id"));
    }

    @Test
    @DisplayName("a storage of identifiers is handed back untouched")
    void identity() {
        Schema plain = Schema.ofObject("t").setProperties(Map.of("amount", Schema.ofDouble("amount")));

        assertSame(plain, MySQLColumnNames.physical(plain));
        assertEquals(Map.of(), MySQLColumnNames.fieldNames(plain));

        Map<String, StorageColumnDefinition> defs = Map.of("amount", new StorageColumnDefinition());
        assertSame(defs, MySQLColumnNames.columnDefinitions(defs));
    }

    @Test
    @DisplayName("column definitions written against the field apply to its column")
    void columnDefinitions() {
        StorageColumnDefinition def = new StorageColumnDefinition();
        assertSame(def, MySQLColumnNames.columnDefinitions(Map.of("IFSC Code", def)).get("IFSC_Code"));
    }

    @Test
    @DisplayName("a filter names the field and reads the column")
    void filters() {
        // The resolver is built from the physical schema, so it knows columns.
        MySQLFieldResolver resolver = MySQLFieldResolver.of(Set.of("Bank_Details"));

        assertEquals("select `IFSC_Code`", sql(resolver.resolve("IFSC Code")));
        assertTrue(sql(resolver.resolve("Bank Details.city")).contains("`Bank_Details`"));
    }

    @Test
    @DisplayName("a joined side comes back keyed by the target's fields")
    void joined() {
        JoinedTable join = new JoinedTable(
                "pay",
                DSL.table(DSL.name("db", "payments")),
                "pay",
                "_id",
                JoinType.LEFT,
                Map.of("_id", "CHAR(26)", "IFSC_Code", "VARCHAR(20)"),
                Set.of(),
                Set.of(),
                Map.of("IFSC_Code", "IFSC Code"));

        Map<String, Object> flat = new LinkedHashMap<>();
        flat.put("_id", "parent");
        flat.put("pay._id", "child");
        flat.put("pay.IFSC_Code", "HDFC0000001");

        @SuppressWarnings("unchecked")
        Map<String, Object> side =
                (Map<String, Object>) MySQLJoinPlanner.nest(flat, List.of(join)).get("pay");

        assertEquals("HDFC0000001", side.get("IFSC Code"));
        assertEquals("select `pay`.`IFSC_Code`", sql(join.column("IFSC Code")));
    }

    private static String sql(org.jooq.Field<?> f) {
        return DSL.using(org.jooq.SQLDialect.MYSQL).select(f).getSQL(ParamType.INLINED);
    }
}
