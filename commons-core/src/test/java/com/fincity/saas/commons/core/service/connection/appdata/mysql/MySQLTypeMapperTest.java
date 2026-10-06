package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.string.StringFormat;
import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.nocode.kirun.engine.json.schema.type.Type;
import com.google.gson.JsonPrimitive;

class MySQLTypeMapperTest {

    private static Schema obj(Map<String, Schema> props) {
        return new Schema().setType(Type.of(SchemaType.OBJECT)).setProperties(props);
    }

    private static MySQLColumn only(Schema field) {
        List<MySQLColumn> cols = MySQLTypeMapper.columns(obj(Map.of("f", field)));
        assertEquals(1, cols.size());
        return cols.getFirst();
    }

    @Nested
    class Scalars {

        @Test
        void mapsEachNumericTypeDistinctly() {
            assertEquals("INT", only(Schema.ofInteger("f")).type());
            assertEquals("BIGINT", only(Schema.ofLong("f")).type());
            assertEquals("FLOAT", only(Schema.ofFloat("f")).type());
            assertEquals("DOUBLE", only(Schema.ofDouble("f")).type());
        }

        @Test
        void mapsBoolean() {
            assertEquals("TINYINT(1)", only(Schema.ofBoolean("f")).type());
        }

        @Test
        void nestedDataBecomesJsonAndSaysWhy() {
            MySQLColumn c = only(new Schema().setType(Type.of(SchemaType.OBJECT)));
            assertEquals("JSON", c.type());
            assertNotNull(c.note(), "an author should be told JSON is not joinable");
        }
    }

    @Nested
    class Strings {

        @Test
        void declaredLengthBecomesVarchar() {
            MySQLColumn c = only(Schema.ofString("f").setMaxLength(120));
            assertEquals("VARCHAR(120)", c.type());
            assertNull(c.note(), "a well-specified string needs no caveat");
        }

        @Test
        void noLengthBecomesTextWithACaveat() {
            MySQLColumn c = only(Schema.ofString("f"));
            assertEquals("TEXT", c.type());
            assertTrue(c.note().contains("maxLength"), "the caveat should say how to fix it");
        }

        @Test
        void absurdLengthFallsBackToText() {
            MySQLColumn c = only(Schema.ofString("f").setMaxLength(100000));
            assertEquals("TEXT", c.type());
            assertNotNull(c.note());
        }

        @Test
        void nonsenseLengthDoesNotProduceNonsenseDdl() {
            MySQLColumn c = only(Schema.ofString("f").setMaxLength(0));
            assertEquals("VARCHAR(255)", c.type());
            assertNotNull(c.note());
        }

        @Test
        void anEnumIsSizedToItsLongestValue() {
            MySQLColumn c = only(Schema.ofString("f")
                    .setEnums(List.of(new JsonPrimitive("OPEN"), new JsonPrimitive("IN_PROGRESS"))));
            assertEquals("VARCHAR(11)", c.type());
            assertNull(c.note());
        }

        @Test
        void aDeclaredLengthWinsOverAnEnum() {
            assertEquals(
                    "VARCHAR(40)",
                    only(Schema.ofString("f").setMaxLength(40).setEnums(List.of(new JsonPrimitive("OPEN"))))
                            .type());
        }

        @Test
        void anEnumWithANonStringValueIsNotSizedFromIt() {
            assertEquals(
                    "TEXT",
                    only(Schema.ofString("f").setEnums(List.of(new JsonPrimitive("OPEN"), new JsonPrimitive(3))))
                            .type());
        }

        @Test
        void anEmailWithNoLengthIsStillIndexable() {
            MySQLColumn c = only(Schema.ofString("f").setFormat(StringFormat.EMAIL));
            assertEquals("VARCHAR(320)", c.type());
            assertNull(c.note());
        }
    }

    @Nested
    class Dates {

        // The point of the relational backend: a declared date is a real date column,
        // so none of the epoch-encoding machinery the Mongo backend needs applies.
        @Test
        void declaredDateFormatsBecomeRealDateColumns() {
            assertEquals("DATETIME(3)", only(Schema.ofString("f").setFormat(StringFormat.DATETIME)).type());
            assertEquals("DATE", only(Schema.ofString("f").setFormat(StringFormat.DATE)).type());
            assertEquals("TIME(3)", only(Schema.ofString("f").setFormat(StringFormat.TIME)).type());
        }

        @Test
        void formatWinsOverLength() {
            assertEquals(
                    "DATETIME(3)",
                    only(Schema.ofString("f").setFormat(StringFormat.DATETIME).setMaxLength(40)).type());
        }

        @Test
        void aFormatThatIsNotADateIsStillAString() {
            assertEquals("VARCHAR(80)", only(Schema.ofString("f").setFormat(StringFormat.EMAIL).setMaxLength(80)).type());
        }
    }

    @Nested
    class Nullability {

        @Test
        void requiredFieldsAreNotNull() {
            Schema s = obj(Map.of("a", Schema.ofInteger("a"), "b", Schema.ofInteger("b")))
                    .setRequired(List.of("a"));
            Map<String, MySQLColumn> byName = new java.util.HashMap<>();
            MySQLTypeMapper.columns(s).forEach(c -> byName.put(c.name(), c));

            assertTrue(byName.get("a").ddl().endsWith("NOT NULL"));
            assertTrue(byName.get("b").ddl().endsWith("NULL"));
            assertTrue(!byName.get("b").ddl().endsWith("NOT NULL"));
        }
    }

    @Nested
    class Edges {

        @Test
        void theIdColumnIsNeverDerivedFromTheSchema() {
            // _id is fixed and is the primary key, so a schema that declares it must not
            // produce a second, conflicting column.
            List<MySQLColumn> cols =
                    MySQLTypeMapper.columns(obj(Map.of("_id", Schema.ofString("_id"), "name", Schema.ofString("name"))));
            assertEquals(1, cols.size());
            assertEquals("name", cols.getFirst().name());
        }

        @Test
        void outputIsOrderedSoDdlIsStableAcrossRuns() {
            List<MySQLColumn> cols = MySQLTypeMapper.columns(obj(Map.of(
                    "zebra", Schema.ofInteger("zebra"),
                    "apple", Schema.ofInteger("apple"),
                    "mango", Schema.ofInteger("mango"))));
            assertEquals(List.of("apple", "mango", "zebra"), cols.stream().map(MySQLColumn::name).toList());
        }

        @Test
        void aUnionOfRealTypesCannotBeATypedColumn() {
            MySQLColumn c = only(new Schema().setType(Type.of(SchemaType.STRING, SchemaType.INTEGER)));
            assertEquals("JSON", c.type());
            assertTrue(c.note().contains("several types"));
        }

        @Test
        void nullAlongsideOneTypeIsJustNullabilityNotAUnion() {
            MySQLColumn c = only(new Schema().setType(Type.of(SchemaType.STRING, SchemaType.NULL)).setMaxLength(50));
            assertEquals("VARCHAR(50)", c.type(), "NULL in the type list must not force JSON");
        }

        @Test
        void emptyAndNullSchemasProduceNoColumns() {
            assertTrue(MySQLTypeMapper.columns(null).isEmpty());
            assertTrue(MySQLTypeMapper.columns(new Schema()).isEmpty());
            assertTrue(MySQLTypeMapper.columns(obj(Map.of())).isEmpty());
        }

        @Test
        void ddlQuotesTheColumnName() {
            assertEquals("`f` INT NULL", only(Schema.ofInteger("f")).ddl());
        }
    }
}
