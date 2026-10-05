package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fincity.saas.commons.core.service.connection.appdata.mysql.SchemaChange.Kind;

class MySQLTablePlannerTest {

    private static MySQLColumn col(String name, String type) {
        return new MySQLColumn(name, type, true);
    }

    private static MySQLColumn notNull(String name, String type) {
        return new MySQLColumn(name, type, false);
    }

    private static SchemaChange one(List<MySQLColumn> before, List<MySQLColumn> after) {
        List<SchemaChange> cs = MySQLTablePlanner.diff(before, after);
        assertEquals(1, cs.size(), "expected exactly one change, got " + cs);
        return cs.getFirst();
    }

    @Nested
    class CreateTable {

        @Test
        void alwaysHasTheUlidPrimaryKey() {
            String ddl = MySQLTablePlanner.createTable("sales", List.of(col("region", "VARCHAR(40)")));
            assertTrue(ddl.contains("`_id` CHAR(26) NOT NULL"), ddl);
            assertTrue(ddl.contains("PRIMARY KEY (`_id`)"), ddl);
        }

        @Test
        void isIdempotentByConstruction() {
            // Re-running a plan must be the recovery path, so creation cannot fail on
            // a table that is already there.
            assertTrue(MySQLTablePlanner.createTable("t", List.of()).startsWith("CREATE TABLE IF NOT EXISTS"));
        }

        @Test
        void emitsEveryColumn() {
            String ddl = MySQLTablePlanner.createTable(
                    "t", List.of(col("a", "INT"), notNull("b", "VARCHAR(10)")));
            assertTrue(ddl.contains("`a` INT NULL"), ddl);
            assertTrue(ddl.contains("`b` VARCHAR(10) NOT NULL"), ddl);
        }
    }

    @Nested
    class Widening {

        @Test
        void newNullableColumnIsSafe() {
            assertEquals(Kind.WIDENING, one(List.of(), List.of(col("a", "INT"))).kind());
        }

        @Test
        void longerVarcharIsSafe() {
            assertEquals(
                    Kind.WIDENING,
                    one(List.of(col("a", "VARCHAR(10)")), List.of(col("a", "VARCHAR(50)"))).kind());
        }

        @Test
        void varcharToTextIsSafe() {
            assertEquals(Kind.WIDENING, one(List.of(col("a", "VARCHAR(10)")), List.of(col("a", "TEXT"))).kind());
        }

        @Test
        void intToBigintIsSafe() {
            assertEquals(Kind.WIDENING, one(List.of(col("a", "INT")), List.of(col("a", "BIGINT"))).kind());
        }

        @Test
        void droppingNotNullIsSafe() {
            assertEquals(Kind.WIDENING, one(List.of(notNull("a", "INT")), List.of(col("a", "INT"))).kind());
        }

        @Test
        void wideningNeedsNoDataCheck() {
            assertFalse(one(List.of(), List.of(col("a", "INT"))).needsDataCheck());
        }
    }

    @Nested
    class Narrowing {

        @Test
        void theCaseThatStartedAllThis() {
            // A string column holding non-numeric data, retyped to a number. This must
            // never apply without first counting what would fail.
            SchemaChange c = one(List.of(col("amount", "VARCHAR(40)")), List.of(col("amount", "DOUBLE")));
            assertEquals(Kind.NARROWING, c.kind());
            assertTrue(c.needsDataCheck());
        }

        @Test
        void shorterVarcharIsNarrowing() {
            assertEquals(
                    Kind.NARROWING,
                    one(List.of(col("a", "VARCHAR(50)")), List.of(col("a", "VARCHAR(10)"))).kind());
        }

        @Test
        void bigintToIntIsNarrowing() {
            assertEquals(Kind.NARROWING, one(List.of(col("a", "BIGINT")), List.of(col("a", "INT"))).kind());
        }

        @Test
        void textToVarcharIsNarrowing() {
            assertEquals(Kind.NARROWING, one(List.of(col("a", "TEXT")), List.of(col("a", "VARCHAR(10)"))).kind());
        }

        @Test
        void addingNotNullIsNarrowing() {
            SchemaChange c = one(List.of(col("a", "INT")), List.of(notNull("a", "INT")));
            assertEquals(Kind.NARROWING, c.kind());
            assertTrue(c.reason().contains("null"));
        }

        @Test
        void aNewNotNullColumnIsNarrowingNotWidening() {
            // Existing rows have no value for it, so this is not a free addition.
            assertEquals(Kind.NARROWING, one(List.of(), List.of(notNull("a", "INT"))).kind());
        }

        @Test
        void unrecognisedConversionsAreTreatedAsUnsafe() {
            // The classifier is deliberately conservative: anything it does not know to
            // be safe costs one counting query, which is the cheap mistake to make.
            assertEquals(Kind.NARROWING, one(List.of(col("a", "JSON")), List.of(col("a", "DATETIME(3)"))).kind());
        }
    }

    @Nested
    class Destructive {

        @Test
        void removingAColumnIsDestructive() {
            SchemaChange c = one(List.of(col("a", "INT")), List.of());
            assertEquals(Kind.DESTRUCTIVE, c.kind());
            assertTrue(c.needsDataCheck());
        }
    }

    @Nested
    class PlanOrdering {

        @Test
        void addsComeBeforeDrops() {
            // A plan that drops first cannot be abandoned half way without losing data.
            // Recovery depends on every prefix of the plan leaving a working table.
            List<SchemaChange> cs = MySQLTablePlanner.diff(List.of(col("old", "INT")), List.of(col("new", "INT")));
            assertEquals(2, cs.size());
            assertEquals("new", cs.get(0).column());
            assertEquals(Kind.DESTRUCTIVE, cs.get(1).kind());
        }

        @Test
        void identicalSchemasProduceNoPlan() {
            List<MySQLColumn> same = List.of(col("a", "INT"), notNull("b", "VARCHAR(5)"));
            assertTrue(MySQLTablePlanner.diff(same, same).isEmpty());
        }

        @Test
        void typeComparisonIgnoresCase() {
            assertTrue(MySQLTablePlanner.diff(List.of(col("a", "int")), List.of(col("a", "INT"))).isEmpty());
        }
    }
}
