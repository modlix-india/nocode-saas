package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.array.ArraySchemaType;
import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.nocode.kirun.engine.json.schema.type.Type;
import com.fincity.saas.commons.model.condition.FilterCondition;

/**
 * Anything deeper than one value goes in a JSON column, and has to stay usable there.
 *
 * The decision is cheap to make and expensive to get wrong in the quiet direction: a
 * nested field that maps to no column at all loses every write to it without saying
 * anything.
 */
class MySQLNestedStructureTest {

    private static Schema object(Map<String, Schema> props) {
        return new Schema().setType(Type.of(SchemaType.OBJECT)).setProperties(props);
    }

    private static MySQLColumn column(Schema storage, String name) {
        return MySQLTypeMapper.columns(storage).stream()
                .filter(c -> c.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Nested
    @DisplayName("choosing the column")
    class Mapping {

        @Test
        @DisplayName("a nested object is JSON, and the note says how deep")
        void nestedObject() {
            Schema address = object(Map.of(
                    "city", Schema.ofString("city").setMaxLength(60),
                    "pin", Schema.ofString("pin").setMaxLength(6)));

            MySQLColumn c = column(object(Map.of("address", address)), "address");

            assertEquals("JSON", c.type());
            assertTrue(c.note().contains("2 field(s)"), c.note());
        }

        @Test
        @DisplayName("the note says what it costs, because the whole point of MySQL is joins")
        void noteWarnsAboutJoins() {
            MySQLColumn c = column(object(Map.of("address", object(Map.of("city", Schema.ofString("city"))))),
                    "address");

            // An author who needs to join on city can still move it to the top level
            // while nothing is stored. Once rows exist, they cannot.
            assertTrue(c.note().contains("not joinable"), c.note());
            assertTrue(c.note().contains("Lift a field to the top level"), c.note());
        }

        @Test
        @DisplayName("an array is JSON")
        void array() {
            Schema tags = Schema.ofArray("tags", Schema.ofString("tag"));
            assertEquals("JSON", column(object(Map.of("tags", tags)), "tags").type());
        }

        @Test
        @DisplayName("properties with no declared type are still an object")
        void propertiesImplyObject() {
            Schema loose = new Schema().setProperties(Map.of("city", Schema.ofString("city")));

            MySQLColumn c = column(object(Map.of("address", loose)), "address");

            // Omitting `type` beside `properties` is a common way to write an object.
            // Calling it untyped would be true and useless.
            assertEquals("JSON", c.type());
            assertTrue(c.note().contains("it has properties"), c.note());
        }

        @Test
        @DisplayName("items with no declared type are still an array")
        void itemsImplyArray() {
            Schema loose = new Schema().setItems(ArraySchemaType.of(Schema.ofString("x")));

            assertTrue(column(object(Map.of("xs", loose)), "xs").note().contains("array"));
        }

        @Test
        @DisplayName("a ref nothing could resolve is JSON, and the note names the ref")
        void unresolvedRef() {
            MySQLColumn c = column(object(Map.of("thing", Schema.ofRef("App.Missing"))), "thing");

            assertEquals("JSON", c.type());
            assertTrue(c.note().contains("App.Missing"), c.note());
        }

        @Test
        @DisplayName("the JSON columns of a storage can be listed")
        void listed() {
            Schema storage = object(Map.of(
                    "name", Schema.ofString("name").setMaxLength(10),
                    "address", object(Map.of("city", Schema.ofString("city"))),
                    "tags", Schema.ofArray("tags", Schema.ofString("t"))));

            assertEquals(Set.of("address", "tags"), MySQLTypeMapper.jsonColumns(storage));
        }

        @Test
        @DisplayName("a nested object still creates a column, which is the whole risk")
        void columnExists() {
            Schema storage = object(Map.of("address", object(Map.of("city", Schema.ofString("city")))));

            assertEquals(
                    1,
                    MySQLTypeMapper.columns(storage).size(),
                    "a nested field that maps to nothing loses every write to it in silence");
        }
    }

    @Nested
    @DisplayName("filtering into one")
    class Paths {

        private static final Set<String> JSON = Set.of("address");

        private static String sql(FilterCondition fc) {
            return MySQLFilterBuilder.build(fc, JSON).toString();
        }

        @Test
        @DisplayName("a dotted field reads a path inside the JSON column")
        void path() {
            String s = sql(FilterCondition.make("address.city", "Pune"));

            assertTrue(s.contains("json_extract"), s);
            assertTrue(s.contains("$.city"), s);
        }

        @Test
        @DisplayName("the extracted value is unquoted, or it matches nothing")
        void unquoted() {
            // JSON_EXTRACT returns `"Pune"` with the quotes. Comparing that against
            // Pune is false for every row, and a filter that silently matches nothing
            // reads exactly like a filter that correctly matched nothing.
            assertTrue(sql(FilterCondition.make("address.city", "Pune")).contains("json_unquote"));
        }

        @Test
        @DisplayName("a deep path keeps its segments")
        void deepPath() {
            String s = sql(FilterCondition.make("address.geo.lat", "18.5"));
            assertTrue(s.contains("$.geo.lat"), s);
        }

        @Test
        @DisplayName("a dotted field on a column that is not JSON stays a column name")
        void notJson() {
            String s = MySQLFilterBuilder.build(FilterCondition.make("some.name", "x"), JSON)
                    .toString();

            // A field may legitimately contain a dot, and rewriting it would break a
            // filter that works today.
            assertTrue(s.contains("\"some.name\""), s);
        }

        @Test
        @DisplayName("a path that is not a path is refused rather than inlined")
        void refusesJunk() {
            // The path is inlined into the SQL because MySQL takes it as a literal, so
            // it is checked rather than trusted. A stored filter is only as trustworthy
            // as whoever saved it.
            assertThrows(
                    UnsupportedFilterException.class,
                    () -> MySQLFilterBuilder.build(FilterCondition.make("address.c'ty", "x"), JSON));
        }

        @Test
        @DisplayName("a trailing dot is a column name, not an empty path")
        void trailingDot() {
            assertTrue(MySQLFilterBuilder.build(FilterCondition.make("address.", "x"), JSON)
                    .toString()
                    .contains("\"address.\""));
        }

        @Test
        @DisplayName("paths work inside a group, so they compose like any other field")
        void inGroup() {
            String s = MySQLFilterBuilder.build(
                            com.fincity.saas.commons.model.condition.ComplexCondition.and(
                                    FilterCondition.make("address.city", "Pune"),
                                    FilterCondition.make("name", "Asha")),
                            JSON)
                    .toString();

            assertTrue(s.contains("json_extract"), s);
            assertTrue(s.contains("\"name\""), s);
        }

        @Test
        @DisplayName("without the JSON column list a dotted field is still a column name")
        void defaultIsUnchanged() {
            // The single-argument build is what every existing caller uses, and its
            // behaviour must not shift underneath them.
            assertTrue(MySQLFilterBuilder.build(FilterCondition.make("address.city", "Pune"))
                    .toString()
                    .contains("\"address.city\""));
        }

        @Test
        @DisplayName("IN works on a path")
        void inOnPath() {
            FilterCondition fc = FilterCondition.make("address.city", null)
                    .setOperator(com.fincity.saas.commons.model.condition.FilterConditionOperator.IN)
                    .setMultiValue(List.of("Pune", "Mumbai"));

            String s = sql(fc);
            assertTrue(s.contains("json_extract"), s);
            assertTrue(s.contains("Mumbai"), s);
        }
    }
}
