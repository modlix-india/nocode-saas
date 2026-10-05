package com.fincity.saas.commons.core.service.connection.appdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.MySQLColumn;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.MySQLTablePlanner;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.MySQLTypeMapper;

/**
 * A storage field name becomes a MySQL column name, and a column name cannot be
 * bound.
 *
 * This is the one piece of a storage definition that is concatenated into SQL rather
 * than passed as a parameter, and the tenants of every app share one MySQL server, so
 * DDL written by whoever can edit a storage is not confined to their own schema.
 */
@DisplayName("Field names that can become columns")
class StorageFieldNamesTest {

    private static final String HOSTILE = "a` INT, ADD COLUMN `pwned";

    @Nested
    @DisplayName("the rule")
    class Rule {

        @Test
        @DisplayName("ordinary names pass, including the ones the fleet actually uses")
        void ordinary() {
            // All 674 top-level field names in the fleet satisfy this, so turning it
            // on breaks nothing that exists.
            assertTrue(StorageFieldNames.valid("name"));
            assertTrue(StorageFieldNames.valid("createdAt"));
            assertTrue(StorageFieldNames.valid("_id"));
            assertTrue(StorageFieldNames.valid("line_1"));
        }

        @Test
        @DisplayName("a backtick does not, which is the whole point")
        void backtick() {
            assertFalse(StorageFieldNames.valid(HOSTILE));
            assertFalse(StorageFieldNames.valid("a`b"));
        }

        @Test
        @DisplayName("neither do the other shapes that break an identifier")
        void otherShapes() {
            assertFalse(StorageFieldNames.valid("2fast"));
            assertFalse(StorageFieldNames.valid("with space"));
            assertFalse(StorageFieldNames.valid("dotted.path"));
            assertFalse(StorageFieldNames.valid("$where"));
            assertFalse(StorageFieldNames.valid(""));
            assertFalse(StorageFieldNames.valid(null));
            // MySQL stops at 64 characters.
            assertFalse(StorageFieldNames.valid("a".repeat(64)));
            assertTrue(StorageFieldNames.valid("a".repeat(63)));
        }
    }

    @Nested
    @DisplayName("what a query may address")
    class QueryPaths {

        @Test
        @DisplayName("an ordinary field and a nested path both pass")
        void ordinary() {
            // Looser than the column rule on purpose: a filter may reach into a
            // nested object, and those segments are not column names.
            assertTrue(StorageFieldNames.safeQueryPath("name"));
            assertTrue(StorageFieldNames.safeQueryPath("address.city"));
            assertTrue(StorageFieldNames.safeQueryPath("items[0].sku"));
        }

        @Test
        @DisplayName("a dollar-prefixed segment does not, because on Mongo it is an operator")
        void operators() {
            // A filter field is written into the query document as a KEY, so
            // "$where" is not a field at all: it is server-side JavaScript, and it
            // evaluates inside $and as readily as at the top level.
            assertFalse(StorageFieldNames.safeQueryPath("$where"));
            assertFalse(StorageFieldNames.safeQueryPath("$expr"));
            assertFalse(StorageFieldNames.safeQueryPath("a.$where"));
            assertFalse(StorageFieldNames.safeQueryPath(""));
            assertFalse(StorageFieldNames.safeQueryPath(null));
            assertFalse(StorageFieldNames.safeQueryPath("a..b"));
        }
    }

    @Nested
    @DisplayName("what a filter may compare against")
    class FilterValues {

        @Test
        @DisplayName("ordinary values pass, including an embedded document")
        void ordinary() {
            // Comparing a field to a whole subdocument is a real thing to want, so
            // this refuses operator keys rather than maps.
            assertTrue(StorageFieldNames.safeFilterValue("hello"));
            assertTrue(StorageFieldNames.safeFilterValue(42));
            assertTrue(StorageFieldNames.safeFilterValue(null));
            assertTrue(StorageFieldNames.safeFilterValue(Map.of("city", "Hyderabad")));
            assertTrue(StorageFieldNames.safeFilterValue(List.of("a", "b")));
        }

        @Test
        @DisplayName("an operator in the value position does not")
        void operators() {
            // Filters.eq("price", {"$gt": 0}) is not an equality test. It is
            // {price: {$gt: 0}} - a different comparison from the one the operator
            // whitelist approved.
            assertFalse(StorageFieldNames.safeFilterValue(Map.of("$gt", 0)));
            assertFalse(StorageFieldNames.safeFilterValue(Map.of("$ne", Map.of("a", 1))));
        }

        @Test
        @DisplayName("and neither does one buried in a list or a nested map")
        void nested() {
            // Depth is not the point; position is.
            assertFalse(StorageFieldNames.safeFilterValue(List.of(Map.of("$where", "1"))));
            assertFalse(StorageFieldNames.safeFilterValue(Map.of("inner", Map.of("$gt", 1))));
        }
    }

    @Nested
    @DisplayName("where it is enforced")
    class Enforcement {

        @Test
        @DisplayName("a hostile property name is reported at save")
        void reportedAtSave() {
            Schema schema = Schema.ofObject("t").setProperties(Map.of(HOSTILE, Schema.ofInteger(HOSTILE)));

            List<String> problems = StorageFieldNames.problems(schema, Set.of());

            assertEquals(1, problems.size());
            // The message must not echo the name back in a form that reads as code.
            assertFalse(problems.getFirst().contains("`"), problems.getFirst());
        }

        @Test
        @DisplayName("a relation key is checked too, because it becomes a column without being in the schema")
        void relationKeys() {
            assertEquals(
                    1, StorageFieldNames.problems(Schema.ofObject("t"), Set.of(HOSTILE)).size());
        }

        @Test
        @DisplayName("and again at the column, so no other entry point can get past it")
        void refusedAtTheColumn() {
            // Before this, the name went straight through:
            //   ALTER TABLE `db`.`t` ADD COLUMN `a` INT, ADD COLUMN `pwned` INT NULL
            assertThrows(IllegalArgumentException.class, () -> new MySQLColumn(HOSTILE, "INT", true));

            Schema schema = Schema.ofObject("t").setProperties(Map.of(HOSTILE, Schema.ofInteger(HOSTILE)));
            assertThrows(IllegalArgumentException.class, () -> MySQLTypeMapper.columns(schema));
        }

        @Test
        @DisplayName("an ordinary column still renders exactly as it did")
        void ordinaryStillWorks() {
            assertTrue(MySQLTablePlanner.createTable("t", MySQLTypeMapper.columns(Schema.ofObject("t")
                            .setProperties(Map.of("amount", Schema.ofDouble("amount")))))
                    .contains("`amount` DOUBLE"));
        }
    }
}
