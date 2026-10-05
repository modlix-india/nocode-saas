package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.jooq.conf.ParamType;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fincity.saas.commons.model.JoinType;

/**
 * Three spellings share one syntax, and which is meant has to be decided rather than
 * guessed.
 *
 * <pre>
 *   amount           a column on the parent
 *   address.city     a path into a JSON column on the parent
 *   customer.region  a column on a joined table
 * </pre>
 *
 * Getting the precedence wrong does not fail loudly: it produces a query against a
 * column that does not exist, or worse, one that does and means something else.
 */
class MySQLFieldResolverTest {

    private static JoinedTable customers() {
        Map<String, String> types = new LinkedHashMap<>();
        types.put("_id", "CHAR(26)");
        types.put("region", "VARCHAR(20)");
        types.put("profile", "JSON");

        return new JoinedTable(
                "customer",
                DSL.table(DSL.name("CL_app", "customers")),
                "customer",
                "_id",
                JoinType.LEFT,
                types,
                Set.of("profile"),
                Set.of());
    }

    private static MySQLFieldResolver joined() {
        Map<String, JoinedTable> joins = new LinkedHashMap<>();
        joins.put("customer", customers());
        return MySQLFieldResolver.of(MySQLJoinPlanner.PARENT, Set.of("address"), joins);
    }

    private static String sql(MySQLFieldResolver r, String name) {
        return DSL.using(org.jooq.SQLDialect.MYSQL)
                .select(r.resolve(name))
                .getSQL(ParamType.INLINED);
    }

    @Test
    @DisplayName("with no joins nothing is qualified, so existing SQL is unchanged")
    void unqualifiedWithoutJoins() {
        MySQLFieldResolver r = MySQLFieldResolver.of(Set.of("address"));

        assertTrue(sql(r, "amount").contains("`amount`"));
        assertTrue(sql(r, "amount").contains("select `amount`"));
    }

    @Test
    @DisplayName("with joins every parent column is qualified")
    void qualifiedWithJoins() {
        // Both sides have an _id, so an unqualified reference is ambiguous to MySQL
        // and the query fails rather than guessing.
        assertTrue(sql(joined(), "amount").contains("`p`.`amount`"));
        assertTrue(sql(joined(), "_id").contains("`p`.`_id`"));
    }

    @Test
    @DisplayName("a join alias wins over a JSON path")
    void joinBeatsJsonPath() {
        String s = sql(joined(), "customer.region");

        assertTrue(s.contains("`customer`.`region`"), s);
        assertTrue(!s.contains("json_extract"), s);
    }

    @Test
    @DisplayName("a JSON path on the parent still resolves as a path")
    void parentJsonPath() {
        String s = sql(joined(), "address.city");

        assertTrue(s.contains("json_extract"), s);
        assertTrue(s.contains("`p`.`address`"), s);
        assertTrue(s.contains("$.city"), s);
    }

    @Test
    @DisplayName("a JSON path on the far side of a join resolves too")
    void joinedJsonPath() {
        // The one case where three segments are meaningful rather than a mistake.
        String s = sql(joined(), "customer.profile.tier");

        assertTrue(s.contains("json_extract"), s);
        assertTrue(s.contains("`customer`.`profile`"), s);
        assertTrue(s.contains("$.tier"), s);
    }

    @Test
    @DisplayName("a joined column that is not JSON keeps its dots as a column name")
    void joinedNonJsonDotted() {
        String s = sql(joined(), "customer.odd.name");
        assertTrue(s.contains("`customer`.`odd.name`"), s);
    }

    @Test
    @DisplayName("a dotted field matching neither is left as a column name")
    void unknownDotted() {
        // A field name may legitimately contain a dot, and rewriting one that matches
        // nothing would break a filter that works today.
        assertTrue(sql(joined(), "some.thing").contains("`p`.`some.thing`"));
    }

    @Test
    @DisplayName("a trailing dot is a column name, not an empty reference")
    void trailingDot() {
        assertTrue(sql(joined(), "customer.").contains("`p`.`customer.`"));
    }

    @Test
    @DisplayName("the bare relation name is still the id column")
    void bareRelationIsTheColumn() {
        // The alias shadows it only for `alias.field`. `customer` on its own is the
        // id the join was reached through, which is what a filter on it means.
        assertTrue(sql(joined(), "customer").contains("`p`.`customer`"), sql(joined(), "customer"));
    }

    @Test
    @DisplayName("a path that is not a path is refused rather than inlined")
    void refusesJunk() {
        assertThrows(UnsupportedFilterException.class, () -> joined().resolve("address.c'ty"));
        assertThrows(UnsupportedFilterException.class, () -> joined().resolve("customer.profile.t'er"));
    }

    @Test
    @DisplayName("hasJoins says whether anything needs qualifying")
    void hasJoins() {
        assertEquals(false, MySQLFieldResolver.of(Set.of()).hasJoins());
        assertEquals(true, joined().hasJoins());
    }
}
