package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The fingerprint the migration journal is keyed on.
 *
 * It replaced the storage's version number, because a version only moves when the
 * STORAGE is edited and the table's shape also changes when a schema it references
 * does, or when a client's override does. Keyed on the version, a schema edit finds a
 * row already marked applied and does nothing at all.
 */
class MySQLShapeKeyTest {

    private static final MySQLColumn NAME = new MySQLColumn("name", "VARCHAR(60)", true);
    private static final MySQLColumn AGE = new MySQLColumn("age", "INT", true);

    @Test
    @DisplayName("the same columns give the same fingerprint")
    void stable() {
        assertEquals(
                MySQLTablePlanner.shapeOf(List.of(NAME, AGE)), MySQLTablePlanner.shapeOf(List.of(NAME, AGE)));
    }

    @Test
    @DisplayName("order does not matter")
    void orderIndependent() {
        // Column order comes from a map walk, and a fingerprint that changed with it
        // would re-run a migration on every publish for no reason at all.
        assertEquals(
                MySQLTablePlanner.shapeOf(List.of(NAME, AGE)), MySQLTablePlanner.shapeOf(List.of(AGE, NAME)));
    }

    @Test
    @DisplayName("a type change changes it")
    void typeChange() {
        assertNotEquals(
                MySQLTablePlanner.shapeOf(List.of(NAME)),
                MySQLTablePlanner.shapeOf(List.of(new MySQLColumn("name", "VARCHAR(120)", true))));
    }

    @Test
    @DisplayName("a nullability change changes it")
    void nullabilityChange() {
        assertNotEquals(
                MySQLTablePlanner.shapeOf(List.of(NAME)),
                MySQLTablePlanner.shapeOf(List.of(new MySQLColumn("name", "VARCHAR(60)", false))));
    }

    @Test
    @DisplayName("an added column changes it")
    void addedColumn() {
        assertNotEquals(MySQLTablePlanner.shapeOf(List.of(NAME)), MySQLTablePlanner.shapeOf(List.of(NAME, AGE)));
    }

    @Test
    @DisplayName("a renamed column changes it")
    void renamedColumn() {
        assertNotEquals(
                MySQLTablePlanner.shapeOf(List.of(NAME)),
                MySQLTablePlanner.shapeOf(List.of(new MySQLColumn("title", "VARCHAR(60)", true))));
    }

    @Test
    @DisplayName("case in the type does not matter, because that is how MySQL reports it")
    void typeCaseInsensitive() {
        // information_schema returns `varchar(60)` lowercase while the mapper produces
        // `VARCHAR(60)`. If those fingerprinted differently, every tenant would look
        // like it needed migrating on every publish forever.
        assertEquals(
                MySQLTablePlanner.shapeOf(List.of(NAME)),
                MySQLTablePlanner.shapeOf(List.of(new MySQLColumn("name", "varchar(60)", true))));
    }

    @Test
    @DisplayName("a note on a column does not change it")
    void noteIgnored() {
        assertEquals(
                MySQLTablePlanner.shapeOf(List.of(NAME)),
                MySQLTablePlanner.shapeOf(
                        List.of(new MySQLColumn("name", "VARCHAR(60)", true, "declare maxLength"))));
    }

    @Test
    @DisplayName("an empty table has a fingerprint rather than a blank")
    void empty() {
        assertEquals(32, MySQLTablePlanner.shapeOf(List.of()).length());
        assertEquals(32, MySQLTablePlanner.shapeOf(null).length());
        assertNotEquals(MySQLTablePlanner.shapeOf(List.of()), MySQLTablePlanner.shapeOf(List.of(NAME)));
    }
}
