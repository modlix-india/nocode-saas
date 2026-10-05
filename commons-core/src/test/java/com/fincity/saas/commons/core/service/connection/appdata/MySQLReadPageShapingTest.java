package com.fincity.saas.commons.core.service.connection.appdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.nocode.kirun.engine.json.schema.type.Type;
import com.fincity.saas.commons.model.Query;

/** What readPage selects and how it orders, which are the two decisions with judgment in them. */
class MySQLReadPageShapingTest {

    private static Schema storage() {
        Map<String, Schema> props = new LinkedHashMap<>();
        props.put("region", Schema.ofString("region").setMaxLength(20));
        props.put("amount", Schema.ofDouble("amount"));
        props.put("notes", Schema.ofString("notes").setMaxLength(100));
        return new Schema().setType(Type.of(SchemaType.OBJECT)).setProperties(props);
    }

    private static List<String> rendered(List<Field<?>> fields) {
        return fields.stream()
                .map(f -> DSL.using(SQLDialect.MYSQL).render(f))
                .toList();
    }

    private static List<String> renderedOrder(List<OrderField<?>> fields) {
        return fields.stream()
                .map(f -> DSL.using(SQLDialect.MYSQL).render(f).replaceAll("\\s+", " "))
                .toList();
    }

    /** The same storage, with a nested object - which becomes one JSON column. */
    private static Schema withNested() {
        Map<String, Schema> address = new LinkedHashMap<>();
        address.put("city", Schema.ofString("city").setMaxLength(40));

        Map<String, Schema> props = new LinkedHashMap<>();
        props.put("region", Schema.ofString("region").setMaxLength(20));
        props.put("address", new Schema().setType(Type.of(SchemaType.OBJECT)).setProperties(address));
        return new Schema().setType(Type.of(SchemaType.OBJECT)).setProperties(props);
    }

    @Nested
    @DisplayName("sorting by a path into a JSON column")
    class JsonPathOrdering {

        @Test
        @DisplayName("a path into a JSON column sorts, as it has always filtered")
        void jsonPathSorts() {
            // Filtering has accepted address.city all along. Sorting checked the
            // name against the column list, did not find it, and silently dropped
            // it - so the rows came back in whatever order the table gave and
            // nothing said the sort had been ignored.
            List<String> out = renderedOrder(MySQLAppDataService.order(
                    withNested(), Sort.by(Sort.Order.asc("address.city"))));

            assertEquals(1, out.size(), out.toString());
            assertTrue(out.getFirst().contains("json_"), out.getFirst());
        }

        @Test
        @DisplayName("and is rendered as a path, not as a qualified column")
        void notAQualifiedColumn() {
            // DSL.name("address.city") would render `address`.`city`, a column on a
            // table called address. Valid SQL, wrong table, and it would only fail
            // at the database.
            String out = renderedOrder(MySQLAppDataService.order(
                            withNested(), Sort.by(Sort.Order.desc("address.city"))))
                    .getFirst();

            assertTrue(!out.contains("`address`.`city`"), out);
            assertTrue(out.contains("desc"), out);
        }

        @Test
        @DisplayName("a dotted name whose head is not a JSON column is still dropped")
        void unknownHeadStillDropped() {
            // Query.DEFAULT_SORT is updatedAt DESC and most storages have no such
            // field; a sort on a name nothing declares must not become SQL.
            assertTrue(MySQLAppDataService.order(withNested(), Sort.by(Sort.Order.asc("nosuch.city")))
                    .isEmpty());
            assertTrue(MySQLAppDataService.order(withNested(), Sort.by(Sort.Order.asc("region.")))
                    .isEmpty());
        }

        @Test
        @DisplayName("an ordinary column is unaffected")
        void plainColumnUnchanged() {
            assertEquals(
                    List.of("`region` asc"),
                    renderedOrder(MySQLAppDataService.order(withNested(), Sort.by(Sort.Order.asc("region")))));
        }
    }

    @Nested
    @DisplayName("projection")
    class Projection {

        @Test
        @DisplayName("no fields means select everything")
        void emptyMeansAll() {
            assertTrue(MySQLAppDataService.projection(storage(), new Query()).isEmpty());
            assertTrue(MySQLAppDataService.projection(storage(), new Query().setFields(List.of()))
                    .isEmpty());
        }

        @Test
        @DisplayName("an include list always carries the id, so rows stay identifiable")
        void includeAlwaysKeepsTheId() {
            List<String> f = rendered(MySQLAppDataService.projection(
                    storage(), new Query().setFields(List.of("region"))));
            assertEquals(List.of("`_id`", "`region`"), f);
        }

        @Test
        @DisplayName("asking for the id explicitly does not duplicate it")
        void idIsNotDuplicated() {
            List<String> f = rendered(MySQLAppDataService.projection(
                    storage(), new Query().setFields(List.of("_id", "region"))));
            assertEquals(List.of("`_id`", "`region`"), f);
        }

        @Test
        @DisplayName("exclude names the columns to drop, and the rest come from the schema")
        void excludeInvertsAgainstTheSchema() {
            List<String> f = rendered(MySQLAppDataService.projection(
                    storage(), new Query().setFields(List.of("notes")).setExcludeFields(true)));
            assertEquals(List.of("`_id`", "`amount`", "`region`"), f);
        }

        @Test
        @DisplayName("excluding everything still returns the id rather than invalid SQL")
        void excludingEverythingLeavesTheId() {
            List<String> f = rendered(MySQLAppDataService.projection(
                    storage(),
                    new Query().setFields(List.of("region", "amount", "notes")).setExcludeFields(true)));
            assertEquals(List.of("`_id`"), f);
        }
    }

    @Nested
    @DisplayName("ordering")
    class Ordering {

        @Test
        @DisplayName("the platform default sort is dropped when the storage has no such column")
        void defaultSortOnAMissingColumnIsDropped() {
            // Query.DEFAULT_SORT is updatedAt DESC and most storages never declare it.
            // Mongo quietly ignores a sort on a missing field. MySQL would fail the whole
            // query, so an invisible default on one backend would break every read on the
            // other.
            assertTrue(MySQLAppDataService.order(storage(), Query.DEFAULT_SORT).isEmpty());
        }

        @Test
        void sortsOnADeclaredColumn() {
            assertEquals(
                    List.of("`amount` desc"),
                    renderedOrder(MySQLAppDataService.order(storage(), Sort.by(Sort.Order.desc("amount")))));
        }

        @Test
        void ascendingIsTheDefaultDirection() {
            assertEquals(
                    List.of("`region` asc"),
                    renderedOrder(MySQLAppDataService.order(storage(), Sort.by(Sort.Order.asc("region")))));
        }

        @Test
        @DisplayName("the id is always sortable even though it is not in the schema")
        void idIsSortable() {
            assertEquals(
                    List.of("`_id` asc"),
                    renderedOrder(MySQLAppDataService.order(storage(), Sort.by(Sort.Order.asc("_id")))));
        }

        @Test
        @DisplayName("a mixed sort keeps the valid parts and drops only the unknown ones")
        void mixedSortIsFiltered() {
            Sort sort = Sort.by(Sort.Order.desc("nosuch"), Sort.Order.asc("region"));
            assertEquals(List.of("`region` asc"), renderedOrder(MySQLAppDataService.order(storage(), sort)));
        }

        @Test
        void unsortedAndNullProduceNoOrderBy() {
            assertTrue(MySQLAppDataService.order(storage(), Sort.unsorted()).isEmpty());
            assertTrue(MySQLAppDataService.order(storage(), null).isEmpty());
        }
    }
}
