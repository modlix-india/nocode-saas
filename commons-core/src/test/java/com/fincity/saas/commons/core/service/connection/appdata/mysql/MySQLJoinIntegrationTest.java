package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fincity.saas.commons.model.AggregateQuery;
import com.fincity.saas.commons.model.Aggregation;
import com.fincity.saas.commons.model.GroupByField;
import com.fincity.saas.commons.model.JoinType;
import com.fincity.saas.commons.model.condition.AggregateFunction;
import com.fincity.saas.commons.model.condition.FilterCondition;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Joins running against a real MySQL.
 *
 * "Revenue by customer region" is the query this whole move was for: two storages,
 * one answer, which an aggregation pipeline over a single collection cannot produce
 * at all. Everything else here is the behaviour around it that has to be right before
 * that query can be trusted - which rows survive a LEFT join, what a filter on the
 * far side does to the count, and whether a self-join addresses the right side.
 */
@Testcontainers
class MySQLJoinIntegrationTest extends AbstractMySQLIntegrationTest {


    private static final String DB = "joindata";

    private static DSLContext ctx;

    @BeforeAll
    static void start() {
        ctx = mysql();
        schema("joindata");

        exec("DROP TABLE IF EXISTS `" + DB + "`.`orders`");
        exec("DROP TABLE IF EXISTS `" + DB + "`.`customers`");
        exec("DROP TABLE IF EXISTS `" + DB + "`.`nodes`");

        exec("CREATE TABLE `" + DB + "`.`customers` (`_id` CHAR(26) NOT NULL, `name` VARCHAR(40) NULL,"
                + " `region` VARCHAR(20) NULL, `profile` JSON NULL, PRIMARY KEY (`_id`))");
        exec("CREATE TABLE `" + DB + "`.`orders` (`_id` CHAR(26) NOT NULL, `amount` DOUBLE NULL,"
                + " `customer` CHAR(26) NULL, PRIMARY KEY (`_id`))");
        exec("CREATE TABLE `" + DB + "`.`nodes` (`_id` CHAR(26) NOT NULL, `label` VARCHAR(20) NULL,"
                + " `parent` CHAR(26) NULL, PRIMARY KEY (`_id`))");

        customer(1, "Asha", "north", "{\"tier\":\"gold\"}");
        customer(2, "Bo", "south", "{\"tier\":\"silver\"}");
        customer(3, "Cy", "north", "{\"tier\":\"gold\"}");

        order(1, 100, 1);
        order(2, 200, 1);
        order(3, 50, 2);
        order(4, 400, 3);
        order(5, 7, null);        // no customer at all
        order(6, 9, 99);          // points at a customer that is not there

        node(1, "root", null);
        node(2, "child", 1);
    }

    private static String id(int i) {
        return String.format("%026d", i);
    }

    private static void customer(int i, String name, String region, String profile) {
        exec("INSERT INTO `" + DB + "`.`customers` VALUES ('" + id(i) + "', '" + name + "', '" + region + "', '"
                + profile + "')");
    }

    private static void order(int i, double amount, Integer customer) {
        exec("INSERT INTO `" + DB + "`.`orders` VALUES ('" + id(i) + "', " + amount + ", "
                + (customer == null ? "NULL" : "'" + id(customer) + "'") + ")");
    }

    private static void node(int i, String label, Integer parent) {
        exec("INSERT INTO `" + DB + "`.`nodes` VALUES ('" + id(i) + "', '" + label + "', "
                + (parent == null ? "NULL" : "'" + id(parent) + "'") + ")");
    }

    private static void exec(String sql) {
        Mono.from(ctx.query(sql)).block();
    }

    // ---------------------------------------------------------------- helpers

    private static JoinedTable customerJoin(JoinType type) {
        Map<String, String> types = new LinkedHashMap<>();
        types.put("_id", "CHAR(26)");
        types.put("name", "VARCHAR(40)");
        types.put("region", "VARCHAR(20)");
        types.put("profile", "JSON");

        return new JoinedTable(
                "customer",
                DSL.table(DSL.name(DB, "customers")),
                "customer",
                "_id",
                type,
                types,
                Set.of("profile"),
                Set.of());
    }

    private static MySQLFieldResolver resolverFor(List<JoinedTable> joins) {
        Map<String, JoinedTable> byAlias = new LinkedHashMap<>();
        joins.forEach(j -> byAlias.put(j.alias(), j));
        return MySQLFieldResolver.of(MySQLJoinPlanner.PARENT, Set.of(), byAlias);
    }

    private static List<Map<String, Object>> select(
            List<JoinedTable> joins, com.fincity.saas.commons.model.condition.AbstractCondition condition) {

        MySQLFieldResolver resolver = resolverFor(joins);
        List<Field<?>> selection = MySQLJoinPlanner.selection(Set.of("_id", "amount", "customer"), joins);

        return Flux.from(ctx.select(selection)
                        .from(MySQLJoinPlanner.from(DSL.table(DSL.name(DB, "orders")), joins))
                        .where(MySQLFilterBuilder.build(condition, resolver)))
                .map(r -> MySQLJoinPlanner.nest(new LinkedHashMap<>(r.intoMap()), joins))
                .collectList()
                .block();
    }

    private static List<Map<String, Object>> aggregate(List<JoinedTable> joins, AggregateQuery q) {
        MySQLFieldResolver resolver = resolverFor(joins);

        return Flux.from(MySQLAggregateBuilder.page(
                        ctx,
                        MySQLJoinPlanner.from(DSL.table(DSL.name(DB, "orders")), joins),
                        q,
                        MySQLFilterBuilder.build(q.getCondition(), resolver),
                        q.getHaving() == null ? null : MySQLFilterBuilder.build(q.getHaving(), Set.of()),
                        q.getPageable(),
                        resolver))
                .map(r -> (Map<String, Object>) new LinkedHashMap<>(r.intoMap()))
                .collectList()
                .block();
    }

    private static Aggregation agg(AggregateFunction f, String field, String alias) {
        return new Aggregation().setFunction(f).setField(field).setAlias(alias);
    }

    // ---------------------------------------------------------------- tests

    @Test
    @DisplayName("a LEFT join keeps every order, matched or not")
    void leftKeepsEveryone() {
        List<Map<String, Object>> rows = select(List.of(customerJoin(JoinType.LEFT)), null);

        assertEquals(6, rows.size(), "all six orders, including the two with no customer row");
    }

    @Test
    @DisplayName("an INNER join drops the orders that match nothing")
    void innerDropsUnmatched() {
        List<Map<String, Object>> rows = select(List.of(customerJoin(JoinType.INNER)), null);

        // Order 5 has a null customer and order 6 points at a row that is not there.
        assertEquals(4, rows.size());
    }

    @Test
    @DisplayName("the related row comes back as an object under the id's own name")
    void nestedObject() {
        List<Map<String, Object>> rows = select(
                List.of(customerJoin(JoinType.INNER)), FilterCondition.make("_id", id(1)));

        assertEquals(1, rows.size());
        Object customer = rows.get(0).get("customer");

        // Which is exactly what the eager fetch produces, so a consumer cannot tell
        // which path served it.
        assertTrue(customer instanceof Map, String.valueOf(customer));
        assertEquals("Asha", ((Map<?, ?>) customer).get("name"));
        assertEquals("north", ((Map<?, ?>) customer).get("region"));
    }

    @Test
    @DisplayName("an unmatched LEFT join leaves no customer key, not a dangling id")
    void unmatchedLeavesNothing() {
        List<Map<String, Object>> rows = select(
                List.of(customerJoin(JoinType.LEFT)), FilterCondition.make("_id", id(6)));

        assertEquals(1, rows.size());
        // Order 6 holds an id for a customer that does not exist. A dangling id looks
        // like data; an absent key looks like what it is.
        assertFalse(rows.get(0).containsKey("customer"), String.valueOf(rows.get(0)));
    }

    @Test
    @DisplayName("the parent can be filtered by a column on the other side")
    void filterAcrossTheJoin() {
        List<Map<String, Object>> rows = select(
                List.of(customerJoin(JoinType.INNER)), FilterCondition.make("customer.region", "north"));

        // This is the thing eager cannot do at all: it expands a relation after the
        // page has been chosen, so it can never narrow the page.
        assertEquals(3, rows.size(), "orders 1, 2 and 4");
    }

    @Test
    @DisplayName("a JSON path on the far side of the join works")
    void filterOnJoinedJsonPath() {
        List<Map<String, Object>> rows = select(
                List.of(customerJoin(JoinType.INNER)), FilterCondition.make("customer.profile.tier", "silver"));

        assertEquals(1, rows.size());
        assertEquals(50d, ((Number) rows.get(0).get("amount")).doubleValue());
    }

    @Test
    @DisplayName("filtering on the bare relation name still means the id column")
    void bareRelationIsTheId() {
        List<Map<String, Object>> rows = select(
                List.of(customerJoin(JoinType.LEFT)), FilterCondition.make("customer", id(1)));

        assertEquals(2, rows.size());
    }

    @Test
    @DisplayName("revenue by customer region: the query the move was for")
    void revenueByJoinedRegion() {
        List<Map<String, Object>> rows = aggregate(
                List.of(customerJoin(JoinType.INNER)),
                new AggregateQuery()
                        .setGroupBy(List.of(new GroupByField().setField("customer.region").setAlias("region")))
                        .setAggregations(List.of(agg(AggregateFunction.SUM, "amount", "total"))));

        // Group by a column of one storage, sum a column of another. A Mongo
        // aggregation pipeline sees one collection and cannot express this.
        assertEquals(2, rows.size());

        Map<String, Double> byRegion = new LinkedHashMap<>();
        rows.forEach(r -> byRegion.put(
                String.valueOf(r.get("region")), ((Number) r.get("total")).doubleValue()));

        assertEquals(700d, byRegion.get("north"), "100 + 200 + 400");
        assertEquals(50d, byRegion.get("south"));
    }

    @Test
    @DisplayName("a measure can come from the joined side too")
    void measureFromTheJoinedSide() {
        List<Map<String, Object>> rows = aggregate(
                List.of(customerJoin(JoinType.INNER)),
                new AggregateQuery()
                        .setGroupBy(List.of(new GroupByField().setField("customer.region").setAlias("region")))
                        .setAggregations(List.of(agg(AggregateFunction.COUNT, "customer.name", "named"))));

        assertEquals(2, rows.size());
    }

    @Test
    @DisplayName("a LEFT join puts the unmatched orders in a null group rather than losing them")
    void leftJoinKeepsUnmatchedInAggregate() {
        List<Map<String, Object>> rows = aggregate(
                List.of(customerJoin(JoinType.LEFT)),
                new AggregateQuery()
                        .setGroupBy(List.of(new GroupByField().setField("customer.region").setAlias("region")))
                        .setAggregations(List.of(agg(AggregateFunction.SUM, "amount", "total"))));

        // Three groups: north, south, and the two orders with no customer. Which of
        // LEFT and INNER you want is the difference between "revenue by region" and
        // "revenue by region, of the orders that have one".
        assertEquals(3, rows.size());

        double orphaned = rows.stream()
                .filter(r -> r.get("region") == null)
                .mapToDouble(r -> ((Number) r.get("total")).doubleValue())
                .sum();

        assertEquals(16d, orphaned, "7 + 9");
    }

    @Test
    @DisplayName("counting groups across a join counts groups")
    void countAcrossTheJoin() {
        AggregateQuery q = new AggregateQuery()
                .setGroupBy(List.of(new GroupByField().setField("customer.region").setAlias("region")))
                .setAggregations(List.of(agg(AggregateFunction.SUM, "amount", "total")));

        List<JoinedTable> joins = List.of(customerJoin(JoinType.INNER));
        MySQLFieldResolver resolver = resolverFor(joins);

        Number n = Mono.from(MySQLAggregateBuilder.count(
                        ctx,
                        MySQLJoinPlanner.from(DSL.table(DSL.name(DB, "orders")), joins),
                        q,
                        DSL.noCondition(),
                        null,
                        resolver))
                .map(r -> (Number) r.get(0))
                .block();

        assertEquals(2L, n.longValue(), "two regions, not four orders");
    }

    @Test
    @DisplayName("a storage can join to itself")
    void selfJoin() {
        Map<String, String> types = new LinkedHashMap<>();
        types.put("_id", "CHAR(26)");
        types.put("label", "VARCHAR(20)");

        JoinedTable parent = new JoinedTable(
                "up",
                DSL.table(DSL.name(DB, "nodes")),
                "parent",
                "_id",
                JoinType.INNER,
                types,
                Set.of(),
                Set.of());

        MySQLFieldResolver resolver = resolverFor(List.of(parent));

        List<Map<String, Object>> rows = Flux.from(ctx.select(
                        MySQLJoinPlanner.selection(Set.of("_id", "label", "parent"), List.of(parent)))
                        .from(MySQLJoinPlanner.from(DSL.table(DSL.name(DB, "nodes")), List.of(parent)))
                        .where(MySQLFilterBuilder.build(FilterCondition.make("up.label", "root"), resolver)))
                .map(r -> MySQLJoinPlanner.nest(new LinkedHashMap<>(r.intoMap()), List.of(parent)))
                .collectList()
                .block();

        // monkbars.tportTree.parent points at tportTree, so this is a real shape and
        // the reason the alias exists at all: without it both sides are `nodes`.
        assertEquals(1, rows.size());
        assertEquals("child", rows.get(0).get("label"));
    }

    @Test
    @DisplayName("two joins to the same storage address different rows")
    void twoJoinsOneTarget() {
        JoinedTable a = customerJoin(JoinType.LEFT);
        Map<String, String> types = new LinkedHashMap<>(a.columnTypes());
        JoinedTable b = new JoinedTable(
                "alt", a.table(), "customer", "_id", JoinType.LEFT, types, Set.of("profile"), Set.of());

        List<Map<String, Object>> rows = select(List.of(a, b), FilterCondition.make("_id", id(1)));

        assertEquals(1, rows.size());
        assertEquals("Asha", ((Map<?, ?>) rows.get(0).get("customer")).get("name"));
        assertEquals("Asha", ((Map<?, ?>) rows.get(0).get("alt")).get("name"));
    }
}
