package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jooq.Table;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.enums.StorageRelationType;
import com.fincity.saas.commons.core.model.StorageRelation;
import com.fincity.saas.commons.model.JoinType;
import com.fincity.saas.commons.model.StorageJoin;

/**
 * What a join is allowed to be, and what it turns into.
 *
 * The validation half is the security-relevant one: the relation name chooses which
 * table is read and the alias becomes an identifier in the SQL, both arriving from a
 * stored definition rather than from code.
 */
class MySQLJoinPlannerTest {

    private static final Table<?> ORDERS = DSL.table(DSL.name("CL_app", "orders"));
    private static final Table<?> CUSTOMERS = DSL.table(DSL.name("CL_app", "customers"));

    private static final Set<String> PARENT_COLUMNS = Set.of("_id", "amount", "customer");

    private static Storage storageWith(Map<String, StorageRelation> relations) {
        Storage s = new Storage();
        s.setName("orders");
        s.setRelations(relations);
        return s;
    }

    private static StorageRelation toOne(String target) {
        return new StorageRelation()
                .setStorageName(target)
                .setRelationType(StorageRelationType.TO_ONE)
                .setFieldName("_id");
    }

    private static StorageRelation toMany(String target) {
        return new StorageRelation()
                .setStorageName(target)
                .setRelationType(StorageRelationType.TO_MANY)
                .setFieldName("_id");
    }

    private static JoinedTable joined(String alias, JoinType type, String... columns) {
        Map<String, String> types = new LinkedHashMap<>();
        types.put("_id", "CHAR(26)");
        for (String c : columns) types.put(c, "VARCHAR(40)");
        return new JoinedTable(
                alias, CUSTOMERS, "customer", "_id", type, types, Set.of(), Set.of());
    }

    @Nested
    @DisplayName("what is allowed")
    class Validation {

        @Test
        @DisplayName("no joins is fine")
        void none() {
            assertNull(MySQLJoinPlanner.check(storageWith(Map.of()), null, PARENT_COLUMNS));
            assertNull(MySQLJoinPlanner.check(storageWith(Map.of()), List.of(), PARENT_COLUMNS));
        }

        @Test
        @DisplayName("a TO_ONE relation joins")
        void toOneIsFine() {
            assertNull(MySQLJoinPlanner.check(
                    storageWith(Map.of("customer", toOne("customers"))),
                    List.of(new StorageJoin().setRelation("customer")),
                    PARENT_COLUMNS));
        }

        @Test
        @DisplayName("a relation the storage does not declare is refused")
        void unknownRelation() {
            // The relation name chooses which table is read. A caller who could name
            // an arbitrary one could read any storage of the tenant, whatever its own
            // readAuth said.
            String err = MySQLJoinPlanner.check(
                    storageWith(Map.of("customer", toOne("customers"))),
                    List.of(new StorageJoin().setRelation("supplier")),
                    PARENT_COLUMNS);

            assertNotNull(err);
            assertTrue(err.contains("no relation 'supplier'"), err);
        }

        @Test
        @DisplayName("a TO_MANY relation is refused, with the reason")
        void toManyIsRefused() {
            String err = MySQLJoinPlanner.check(
                    storageWith(Map.of("tags", toMany("tagList"))),
                    List.of(new StorageJoin().setRelation("tags")),
                    PARENT_COLUMNS);

            // It stores a list of ids in one column, so the join cannot use an index.
            // Shipping it quietly would mean a scan of the parent per row.
            assertNotNull(err);
            assertTrue(err.contains("cannot use an index"), err);
        }

        @Test
        @DisplayName("an alias that is not an identifier is refused")
        void badAlias() {
            for (String alias : List.of("has space", "has-dash", "1leading", "has$dollar"))
                assertNotNull(
                        MySQLJoinPlanner.check(
                                storageWith(Map.of("customer", toOne("customers"))),
                                List.of(new StorageJoin().setRelation("customer").setAlias(alias)),
                                PARENT_COLUMNS),
                        "'" + alias + "' should be refused");
        }

        @Test
        @DisplayName("two joins cannot share an alias")
        void duplicateAlias() {
            String err = MySQLJoinPlanner.check(
                    storageWith(Map.of("customer", toOne("customers"), "payer", toOne("customers"))),
                    List.of(
                            new StorageJoin().setRelation("customer").setAlias("c"),
                            new StorageJoin().setRelation("payer").setAlias("c")),
                    PARENT_COLUMNS);

            assertTrue(err.contains("duplicate join alias"), err);
        }

        @Test
        @DisplayName("an alias that is also a column is refused")
        void aliasCollidesWithColumn() {
            // Otherwise `amount.x` would mean a joined column on one client and a
            // JSON path on another, depending on whose storage declares what.
            String err = MySQLJoinPlanner.check(
                    storageWith(Map.of("customer", toOne("customers"))),
                    List.of(new StorageJoin().setRelation("customer").setAlias("amount")),
                    PARENT_COLUMNS);

            assertTrue(err.contains("is also a column"), err);
        }

        @Test
        @DisplayName("the default alias shadows the id column it was reached through, on purpose")
        void aliasMayShadowItsOwnRelation() {
            // Relations are keyed by the parent column holding the id, so the default
            // alias is always also a column. Shadowing it is the existing mental
            // model: eager already replaces the id with the related object under the
            // same name.
            assertNull(MySQLJoinPlanner.check(
                    storageWith(Map.of("customer", toOne("customers"))),
                    List.of(new StorageJoin().setRelation("customer")),
                    PARENT_COLUMNS));
        }

        @Test
        @DisplayName("an alias may not shadow a JSON column, which is the ambiguous case")
        void aliasCannotShadowJson() {
            String err = MySQLJoinPlanner.check(
                    storageWith(Map.of("customer", toOne("customers"))),
                    List.of(new StorageJoin().setRelation("customer").setAlias("address")),
                    Set.of("_id", "amount", "customer", "address"),
                    Set.of("address"));

            assertTrue(err.contains("JSON column"), err);
        }

        @Test
        @DisplayName("the same relation twice is fine when the aliases differ")
        void sameRelationTwice() {
            assertNull(MySQLJoinPlanner.check(
                    storageWith(Map.of("customer", toOne("customers"))),
                    List.of(
                            new StorageJoin().setRelation("customer").setAlias("a"),
                            new StorageJoin().setRelation("customer").setAlias("b")),
                    PARENT_COLUMNS));
        }

        @Test
        @DisplayName("too many joins in one query is refused")
        void tooMany() {
            Map<String, StorageRelation> relations = new LinkedHashMap<>();
            List<StorageJoin> joins = new java.util.ArrayList<>();
            for (int i = 0; i <= MySQLJoinPlanner.MAX_JOINS; i++) {
                relations.put("r" + i, toOne("customers"));
                joins.add(new StorageJoin().setRelation("r" + i).setAlias("a" + i));
            }

            // It arrives from a definition, so nobody is watching when it runs.
            assertNotNull(MySQLJoinPlanner.check(storageWith(relations), joins, PARENT_COLUMNS));
        }

        @Test
        @DisplayName("the alias defaults to the relation name")
        void defaultAlias() {
            assertEquals("customer", new StorageJoin().setRelation("customer").resolvedAlias());
            assertEquals("c", new StorageJoin().setRelation("customer").setAlias("c").resolvedAlias());
        }
    }

    @Nested
    @DisplayName("what it generates")
    class Sql {

        private static String from(List<JoinedTable> joins) {
            return DSL.using(org.jooq.SQLDialect.MYSQL)
                    .select(DSL.asterisk())
                    .from(MySQLJoinPlanner.from(ORDERS, joins))
                    .getSQL(org.jooq.conf.ParamType.INLINED);
        }

        @Test
        @DisplayName("a LEFT join keeps parents that match nothing")
        void leftJoin() {
            String s = from(List.of(joined("c", JoinType.LEFT, "region")));

            // Which is what the eager fetch already does: a row whose relation is
            // null still comes back.
            assertTrue(s.contains("left outer join"), s);
            assertTrue(s.contains("`CL_app`.`customers` as `c`"), s);
            assertTrue(s.contains("`p`.`customer` = `c`.`_id`"), s);
        }

        @Test
        @DisplayName("an INNER join drops them, so the join is a filter")
        void innerJoin() {
            String s = from(List.of(joined("c", JoinType.INNER, "region")));
            assertTrue(s.contains("join `CL_app`.`customers` as `c`"), s);
            assertFalse(s.contains("left outer join"), s);
        }

        @Test
        @DisplayName("the parent is aliased so both sides can be addressed")
        void parentAliased() {
            assertTrue(from(List.of(joined("c", JoinType.LEFT, "region"))).contains("as `p`"));
        }

        @Test
        @DisplayName("two joins both hang off the parent")
        void twoJoins() {
            String s = from(List.of(joined("a", JoinType.LEFT, "x"), joined("b", JoinType.INNER, "y")));
            assertTrue(s.contains("as `a`"), s);
            assertTrue(s.contains("as `b`"), s);
        }

        @Test
        @DisplayName("every column is named, because both sides have an _id")
        void columnsAreNamed() {
            List<org.jooq.Field<?>> fields =
                    MySQLJoinPlanner.selection(Set.of("_id", "amount"), List.of(joined("c", JoinType.LEFT, "region")));

            String s = DSL.using(org.jooq.SQLDialect.MYSQL)
                    .select(fields)
                    .from(ORDERS)
                    .getSQL(org.jooq.conf.ParamType.INLINED);

            // `p.*, c.*` would put two _id columns in one result map and the second
            // would win, so one row's identity would silently become another's.
            assertTrue(s.contains("`p`.`_id`"), s);
            assertTrue(s.contains("`c`.`_id` as `c._id`"), s);
            assertTrue(s.contains("`c`.`region` as `c.region`"), s);
        }
    }

    @Nested
    @DisplayName("the shape that comes back")
    class Nesting {

        @Test
        @DisplayName("joined columns fold into an object under the alias")
        void nests() {
            Map<String, Object> flat = new LinkedHashMap<>();
            flat.put("_id", "1");
            flat.put("amount", 10);
            flat.put("c._id", "9");
            flat.put("c.region", "north");

            flat.put("c", "9");

            Map<String, Object> out = MySQLJoinPlanner.nest(flat, List.of(joined("c", JoinType.LEFT, "region")));

            // The eager fetch already returns a related row as a nested object under
            // the id's own name, and a consumer should not be able to tell which path
            // served it.
            assertEquals("1", out.get("_id"));
            assertTrue(out.get("c") instanceof Map);
            assertEquals("north", ((Map<?, ?>) out.get("c")).get("region"));
        }

        @Test
        @DisplayName("a LEFT join that matched nothing contributes no object at all")
        void unmatchedIsDropped() {
            Map<String, Object> flat = new LinkedHashMap<>();
            flat.put("_id", "1");
            flat.put("c._id", null);
            flat.put("c.region", null);

            flat.put("c", "9");

            Map<String, Object> out = MySQLJoinPlanner.nest(flat, List.of(joined("c", JoinType.LEFT, "region")));

            // An object full of nulls is not the same as no related row, and eager
            // returns nothing in this case. The dangling id goes with it: it looks
            // like data, and an absent key looks like what it is.
            assertFalse(out.containsKey("c"), String.valueOf(out));
        }

        @Test
        @DisplayName("with no joins the row is returned untouched")
        void noJoins() {
            Map<String, Object> flat = new LinkedHashMap<>();
            flat.put("_id", "1");
            assertEquals(flat, MySQLJoinPlanner.nest(flat, List.of()));
        }
    }
}
