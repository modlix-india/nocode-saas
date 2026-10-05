package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.string.StringFormat;
import com.fincity.saas.commons.core.enums.MySQLColumnType;
import com.fincity.saas.commons.core.model.StorageColumnDefinition;

@DisplayName("A declared column type replaces the mapper's guess")
class MySQLColumnOverrideTest {

    private static final Schema SCHEMA = Schema.ofObject("order")
            .setProperties(Map.of(
                    "price", Schema.ofString("price").setFormat(StringFormat.DECIMAL),
                    "note", Schema.ofString("note").setMaxLength(40),
                    "placedAt", Schema.ofString("placedAt").setFormat(StringFormat.DATETIME)));

    private static Map<String, StorageColumnDefinition> defs(String field, StorageColumnDefinition.MySQL mysql) {
        return Map.of(field, new StorageColumnDefinition().setMysql(mysql));
    }

    private static String typeOf(List<MySQLColumn> columns, String name) {
        return columns.stream()
                .filter(c -> c.name().equals(name))
                .findFirst()
                .orElseThrow()
                .type();
    }

    @Nested
    @DisplayName("mapping")
    class Mapping {

        @Test
        @DisplayName("the default for a DECIMAL format is still 19,4")
        void defaultStands() {
            assertEquals("DECIMAL(19,4)", typeOf(MySQLTypeMapper.columns(SCHEMA), "price"));
        }

        @Test
        @DisplayName("a declared precision and scale wins")
        void overrideWins() {
            List<MySQLColumn> columns = MySQLTypeMapper.columns(
                    SCHEMA,
                    defs(
                            "price",
                            new StorageColumnDefinition.MySQL()
                                    .setType(MySQLColumnType.DECIMAL)
                                    .setPrecision(12)
                                    .setScale(6)));

            assertEquals("DECIMAL(12,6)", typeOf(columns, "price"));
            // And only that field. An override is not a second schema.
            assertEquals("VARCHAR(40)", typeOf(columns, "note"));
        }

        @Test
        @DisplayName("nullability still comes from the schema, not the definition")
        void requiredStillRules() {
            Schema required = Schema.ofObject("order")
                    .setProperties(Map.of("price", Schema.ofString("price").setFormat(StringFormat.DECIMAL)))
                    .setRequired(List.of("price"));

            MySQLColumn c = MySQLTypeMapper.columns(
                            required,
                            defs(
                                    "price",
                                    new StorageColumnDefinition.MySQL()
                                            .setType(MySQLColumnType.DECIMAL)
                                            .setPrecision(8)
                                            .setScale(2)))
                    .getFirst();

            assertFalse(c.nullable());
            assertTrue(c.ddl().contains("NOT NULL"), c.ddl());
        }
    }

    @Nested
    @DisplayName("the derived column sets follow the override, not the format")
    class DerivedSets {

        @Test
        @DisplayName("a date field forced to text stops being a date column")
        void dateTurnedIntoText() {
            // Read off the format instead, this would still be decoded as a date and
            // the value would come back mangled from a plain VARCHAR.
            Map<String, StorageColumnDefinition> defs = defs(
                    "placedAt",
                    new StorageColumnDefinition.MySQL().setType(MySQLColumnType.VARCHAR).setLength(40));

            assertTrue(MySQLTypeMapper.dateStringColumns(SCHEMA).contains("placedAt"));
            assertFalse(MySQLTypeMapper.dateStringColumns(SCHEMA, defs).contains("placedAt"));
        }

        @Test
        @DisplayName("a plain string forced to DATETIME becomes one")
        void textTurnedIntoDate() {
            Map<String, StorageColumnDefinition> defs =
                    defs("note", new StorageColumnDefinition.MySQL().setType(MySQLColumnType.DATETIME));

            assertTrue(MySQLTypeMapper.dateStringColumns(SCHEMA, defs).contains("note"));
        }

        @Test
        @DisplayName("decimals are found by column type, however they got there")
        void decimals() {
            assertEquals(Set.of("price"), MySQLTypeMapper.decimalColumns(SCHEMA, null));

            Map<String, StorageColumnDefinition> defs = defs(
                    "note",
                    new StorageColumnDefinition.MySQL()
                            .setType(MySQLColumnType.DECIMAL)
                            .setPrecision(5)
                            .setScale(2));

            assertTrue(MySQLTypeMapper.decimalColumns(SCHEMA, defs).contains("note"));
        }

        @Test
        @DisplayName("a field forced to JSON becomes a JSON column")
        void json() {
            Map<String, StorageColumnDefinition> defs =
                    defs("note", new StorageColumnDefinition.MySQL().setType(MySQLColumnType.JSON));

            assertTrue(MySQLTypeMapper.jsonColumns(SCHEMA, defs).contains("note"));
        }
    }

    @Nested
    @DisplayName("decimals round-trip as the schema describes them")
    class DecimalRoundTrip {

        private final MySQLValueCodec codec = new MySQLValueCodec(new ObjectMapper());

        @Test
        @DisplayName("a BigDecimal comes back as plain text, because the schema says STRING")
        void decodedAsText() {
            // Without this, reading a row and writing it back with one unrelated
            // field changed fails validation on the price, which the caller never
            // touched: Gson renders the BigDecimal as a bare number and the STRING
            // validator refuses it.
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("price", new BigDecimal("10.5000"));

            assertEquals("10.5000", this.codec.decode(row, Set.of(), Set.of(), Set.of("price")).get("price"));
        }

        @Test
        @DisplayName("the stored scale is kept, so a read and a write back are not an edit")
        void trailingZerosKept() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("price", new BigDecimal("10.5000"));

            String out = String.valueOf(
                    this.codec.decode(row, Set.of(), Set.of(), Set.of("price")).get("price"));

            assertFalse(out.contains("E"), out);
            assertEquals("10.5000", out);
        }

        @Test
        @DisplayName("a large value does not become scientific notation, which the pattern would reject")
        void noExponent() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("price", new BigDecimal("1E+3"));

            assertEquals("1000", this.codec.decode(row, Set.of(), Set.of(), Set.of("price")).get("price"));
        }

        @Test
        @DisplayName("a string is bound as an exact decimal on the way in")
        void encodedExactly() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("price", "10.50");

            assertEquals(
                    new BigDecimal("10.50"),
                    this.codec.encode(row, Set.of(), Set.of(), Set.of("price")).get("price"));
        }

        @Test
        @DisplayName("a double goes through its own toString, not the binary value")
        void doubleIsNotTakenLiterally() {
            // new BigDecimal(0.1) is 0.1000000000000000055511151231257827, which is
            // exactly the surprise a DECIMAL column is chosen to avoid.
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("price", 0.1d);

            assertEquals(
                    new BigDecimal("0.1"),
                    this.codec.encode(row, Set.of(), Set.of(), Set.of("price")).get("price"));
        }

        @Test
        @DisplayName("a value that is not a decimal is passed through for MySQL to name")
        void unparseable() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("price", "ten");

            assertEquals("ten", this.codec.encode(row, Set.of(), Set.of(), Set.of("price")).get("price"));
        }
    }
}
