package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fincity.saas.commons.core.enums.MySQLColumnType;
import com.fincity.saas.commons.core.model.StorageColumnDefinition;

@DisplayName("Author-declared MySQL column types")
class MySQLColumnDefinitionsTest {

    private static StorageColumnDefinition.MySQL def(MySQLColumnType type) {
        return new StorageColumnDefinition.MySQL().setType(type);
    }

    @Nested
    @DisplayName("rendering")
    class Rendering {

        @Test
        @DisplayName("a decimal renders with its precision and scale")
        void decimal() {
            assertEquals(
                    "DECIMAL(10,2)",
                    MySQLColumnDefinitions.type(
                            def(MySQLColumnType.DECIMAL).setPrecision(10).setScale(2)));
        }

        @Test
        @DisplayName("unsigned is part of the type, because MySQL reports it that way")
        void unsigned() {
            // COLUMN_TYPE comes back as "decimal(10,2) unsigned", and the migration
            // diff compares against that text. Rendered any other way, every
            // reconcile would see a difference and alter the table again.
            assertEquals(
                    "DECIMAL(10,2) UNSIGNED",
                    MySQLColumnDefinitions.type(def(MySQLColumnType.DECIMAL)
                            .setPrecision(10)
                            .setScale(2)
                            .setUnsigned(Boolean.TRUE)));
        }

        @Test
        @DisplayName("unsigned is dropped for a type where it means nothing")
        void unsignedOnText() {
            assertEquals(
                    "VARCHAR(20)",
                    MySQLColumnDefinitions.type(
                            def(MySQLColumnType.VARCHAR).setLength(20).setUnsigned(Boolean.TRUE)));
        }

        @Test
        @DisplayName("fractional seconds are optional and omitted at zero")
        void fractionalSeconds() {
            assertEquals("DATETIME", MySQLColumnDefinitions.type(def(MySQLColumnType.DATETIME)));
            assertEquals(
                    "DATETIME",
                    MySQLColumnDefinitions.type(def(MySQLColumnType.DATETIME).setPrecision(0)));
            assertEquals(
                    "DATETIME(6)",
                    MySQLColumnDefinitions.type(def(MySQLColumnType.DATETIME).setPrecision(6)));
        }

        @Test
        @DisplayName("a type with no arguments renders as the bare keyword")
        void bare() {
            assertEquals("JSON", MySQLColumnDefinitions.type(def(MySQLColumnType.JSON)));
            assertEquals("BIGINT", MySQLColumnDefinitions.type(def(MySQLColumnType.BIGINT)));
        }

        @Test
        @DisplayName("the collation is not part of the type")
        void collationIsSeparate() {
            // information_schema.COLUMN_TYPE never mentions the collation. Folded in,
            // the rendered type could not match what comes back and the table would
            // be altered on every single reconcile for ever.
            StorageColumnDefinition.MySQL d =
                    def(MySQLColumnType.VARCHAR).setLength(30).setCollation("utf8mb4_bin");

            assertEquals("VARCHAR(30)", MySQLColumnDefinitions.type(d));
            assertEquals("utf8mb4_bin", MySQLColumnDefinitions.collation(d));
        }
    }

    @Nested
    @DisplayName("what is refused")
    class Refusals {

        @Test
        @DisplayName("a decimal without precision and scale, because MySQL would silently store (10,0)")
        void decimalNeedsBoth() {
            List<String> problems = MySQLColumnDefinitions.problems("price", def(MySQLColumnType.DECIMAL));

            assertEquals(1, problems.size());
            assertTrue(problems.getFirst().contains("precision and a scale"), problems.getFirst());
        }

        @Test
        @DisplayName("a varchar without a length")
        void varcharNeedsLength() {
            assertTrue(MySQLColumnDefinitions.problems("name", def(MySQLColumnType.VARCHAR)).stream()
                    .anyMatch(p -> p.contains("needs a length")));
        }

        @Test
        @DisplayName("a scale larger than the precision")
        void scaleBeyondPrecision() {
            assertTrue(MySQLColumnDefinitions.problems(
                            "rate", def(MySQLColumnType.DECIMAL).setPrecision(4).setScale(6))
                    .stream()
                    .anyMatch(p -> p.contains("cannot exceed precision")));
        }

        @Test
        @DisplayName("a char longer than 255, which is a different limit from varchar")
        void charIsCappedLower() {
            assertTrue(MySQLColumnDefinitions.problems("code", def(MySQLColumnType.CHAR).setLength(300)).stream()
                    .anyMatch(p -> p.contains("between 1 and 255")));

            assertTrue(MySQLColumnDefinitions.problems("blurb", def(MySQLColumnType.VARCHAR).setLength(300))
                    .isEmpty());
        }

        @Test
        @DisplayName("arguments a type does not take")
        void strayArguments() {
            assertTrue(MySQLColumnDefinitions.problems("flag", def(MySQLColumnType.JSON).setLength(10)).stream()
                    .anyMatch(p -> p.contains("takes no length")));
        }

        @Test
        @DisplayName("a collation on a column that cannot have one")
        void collationOnNonText() {
            assertTrue(MySQLColumnDefinitions.problems(
                            "n", def(MySQLColumnType.BIGINT).setCollation("utf8mb4_bin"))
                    .stream()
                    .anyMatch(p -> p.contains("only to a text column")));
        }

        @Test
        @DisplayName("a collation that is not an identifier, because it is concatenated into DDL")
        void collationIsNotAnEscapeHatch() {
            // The one place author text could reach SQL. A column type cannot be
            // bound as a parameter, so the only defence is that nothing arbitrary is
            // ever concatenated: the type comes from an enum, the numbers are parsed
            // and re-rendered, and this is matched against a character class.
            StorageColumnDefinition.MySQL d = def(MySQLColumnType.VARCHAR)
                    .setLength(10)
                    .setCollation("utf8mb4_bin, DROP COLUMN secret");

            assertFalse(MySQLColumnDefinitions.problems("x", d).isEmpty());
            assertNull(MySQLColumnDefinitions.collation(d));
        }

        @Test
        @DisplayName("nothing at all, when there is no definition")
        void noDefinition() {
            assertTrue(MySQLColumnDefinitions.problems("x", null).isEmpty());
        }

        @Test
        @DisplayName("a definition with no type")
        void typeRequired() {
            assertTrue(MySQLColumnDefinitions.problems("x", new StorageColumnDefinition.MySQL()).stream()
                    .anyMatch(p -> p.contains("mysql.type is required")));
        }
    }
}
