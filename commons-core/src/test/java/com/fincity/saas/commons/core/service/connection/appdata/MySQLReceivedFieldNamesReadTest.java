package com.fincity.saas.commons.core.service.connection.appdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jooq.QueryPart;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.nocode.kirun.engine.json.schema.type.Type;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.MySQLColumnNames;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.MySQLFieldResolver;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.MySQLFilterBuilder;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.MySQLTypeMapper;
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;

/**
 * Reading back a storage whose field names are not identifiers.
 *
 * A caller names "IFSC Code" in its filter, its sort and its field list, and gets
 * "IFSC Code" back in every row. The table has IFSC_Code. Each test below is one of
 * the places on the read path where the first has to become the second, or the
 * second the first.
 */
@DisplayName("Reading fields stored under a different column name")
class MySQLReceivedFieldNamesReadTest {

    /** Physical, as every read path in the service receives it. */
    private static Schema schema() {
        Map<String, Schema> props = new LinkedHashMap<>();
        props.put("IFSC Code", Schema.ofString("IFSC Code").setMaxLength(20));
        props.put("Virtual Account ", Schema.ofString("Virtual Account ").setMaxLength(40));
        props.put("Amount", Schema.ofDouble("Amount"));
        props.put("Bank Details", new Schema().setType(Type.of(SchemaType.OBJECT)));
        props.put("some_thing", Schema.ofString("some_thing").setMaxLength(20));
        return MySQLColumnNames.physical(
                new Schema().setType(Type.of(SchemaType.OBJECT)).setProperties(props));
    }

    private static MySQLFieldResolver resolver() {
        return MySQLFieldResolver.of(MySQLTypeMapper.jsonColumns(schema()));
    }

    private static String sql(QueryPart part) {
        return DSL.using(SQLDialect.MYSQL).render(part).replaceAll("\\s+", " ");
    }

    private static List<String> rendered(List<? extends QueryPart> parts) {
        return parts.stream().map(MySQLReceivedFieldNamesReadTest::sql).toList();
    }

    @Nested
    @DisplayName("the row that comes back")
    class Rows {

        @Test
        @DisplayName("is keyed by field, with the id and unknown keys left alone")
        void keyedByField() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("_id", "01J");
            row.put("IFSC_Code", "HDFC0000001");
            row.put("Virtual_Account_", "VA-1");
            row.put("Amount", 500.0);

            Map<String, Object> out = MySQLColumnNames.toFields(row, schema());

            assertEquals(List.of("_id", "IFSC Code", "Virtual Account ", "Amount"), List.copyOf(out.keySet()));
            assertEquals("HDFC0000001", out.get("IFSC Code"));
        }

        @Test
        @DisplayName("keeps the names inside a JSON field exactly as they were written")
        void nestedUntouched() {
            // A nested object is one JSON column, so its keys were never columns and
            // are stored verbatim. Only the top level is translated.
            Map<String, Object> bank = Map.of("Branch Name", "Koregaon Park");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("Bank_Details", bank);

            assertEquals(bank, MySQLColumnNames.toFields(row, schema()).get("Bank Details"));
        }
    }

    @Nested
    @DisplayName("a filter")
    class Filters {

        @Test
        @DisplayName("on a spaced field compares its column")
        void spacedField() {
            String where = sql(MySQLFilterBuilder.build(
                    new FilterCondition()
                            .setField("IFSC Code")
                            .setOperator(FilterConditionOperator.EQUALS)
                            .setValue("HDFC0000001"),
                    resolver()));

            assertTrue(where.contains("`IFSC_Code`"), where);
        }

        @Test
        @DisplayName("into a spaced JSON field reads a path in its column")
        void jsonPath() {
            String where = sql(MySQLFilterBuilder.build(
                    new FilterCondition()
                            .setField("Bank Details.city")
                            .setOperator(FilterConditionOperator.EQUALS)
                            .setValue("Pune"),
                    resolver()));

            assertTrue(where.contains("`Bank_Details`"), where);
            assertTrue(where.contains("$.city"), where);
        }
    }

    @Nested
    @DisplayName("a sort")
    class Sorting {

        @Test
        @DisplayName("by a spaced field orders by its column")
        void spacedField() {
            List<String> order = rendered(
                    MySQLAppDataService.order(schema(), Sort.by(Sort.Order.desc("IFSC Code")), resolver()));

            assertEquals(List.of("`IFSC_Code` desc"), order);
        }

        @Test
        @DisplayName("by a path into a spaced JSON field orders by the path")
        void jsonPath() {
            List<String> order = rendered(
                    MySQLAppDataService.order(schema(), Sort.by(Sort.Order.asc("Bank Details.city")), resolver()));

            assertEquals(1, order.size(), order.toString());
            assertTrue(order.getFirst().contains("`Bank_Details`"), order.getFirst());
        }

        @Test
        @DisplayName("by a dotted name nobody declared is still dropped, not read from a look-alike column")
        void dottedIsNotNormalised() {
            // some.thing would normalise to some_thing, which exists. A field cannot
            // contain a dot, so this names nothing and must not quietly sort by it.
            assertEquals(
                    List.of(),
                    rendered(MySQLAppDataService.order(schema(), Sort.by(Sort.Order.asc("some.thing")), resolver())));
        }
    }

    @Nested
    @DisplayName("a field list")
    class Fields {

        @Test
        @DisplayName("selecting spaced fields selects their columns, and the id")
        void include() {
            Query q = new Query().setFields(List.of("IFSC Code", "Amount"));

            assertEquals(List.of("`_id`", "`IFSC_Code`", "`Amount`"), rendered(MySQLAppDataService.projection(schema(), q)));
        }

        @Test
        @DisplayName("excluding a spaced field leaves its column out")
        void exclude() {
            Query q = new Query().setFields(List.of("IFSC Code")).setExcludeFields(true);
            List<String> out = rendered(MySQLAppDataService.projection(schema(), q));

            assertFalse(out.contains("`IFSC_Code`"), out.toString());
            assertTrue(out.contains("`Virtual_Account_`"), out.toString());
        }

        @Test
        @DisplayName("on a joined read, too")
        void joined() {
            Query q = new Query().setFields(List.of("IFSC Code"));

            assertEquals(Set.of("_id", "IFSC_Code"), MySQLAppDataService.selectedParentColumns(schema(), q));
        }
    }

    @Nested
    @DisplayName("an aggregate")
    class Aggregates {

        @Test
        @DisplayName("finds a spaced field among the columns, on this side and across a join")
        void head() {
            Map<String, String> columns = new LinkedHashMap<>();
            columns.put("IFSC_Code", "VARCHAR(20)");
            columns.put("Bank_Details", "JSON");
            columns.put("pay.Remitter_Name", "VARCHAR(100)");

            assertEquals("IFSC_Code", MySQLAppDataService.head("IFSC Code", columns));
            assertEquals("Bank_Details", MySQLAppDataService.head("Bank Details.city", columns));
            assertEquals("pay.Remitter_Name", MySQLAppDataService.head("pay.Remitter Name", columns));
        }
    }
}
