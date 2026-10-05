package com.fincity.saas.core.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.testcontainers.containers.MySQLContainer;

import com.fincity.saas.commons.core.document.Connection;
import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.enums.ConnectionSubType;
import com.fincity.saas.commons.core.enums.ConnectionType;
import com.fincity.saas.commons.core.enums.StorageRelationType;
import com.fincity.saas.commons.core.model.DataObject;
import com.fincity.saas.commons.core.model.StorageRelation;
import com.fincity.saas.commons.core.service.connection.appdata.AppDataService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.AggregateQuery;
import com.fincity.saas.commons.model.Aggregation;
import com.fincity.saas.commons.model.GroupByField;
import com.fincity.saas.commons.model.JoinType;
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.model.StorageJoin;
import com.fincity.saas.commons.model.StorageSubQuery;
import com.fincity.saas.commons.model.condition.AggregateFunction;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import reactor.core.publisher.Mono;

/**
 * Joins through the service, with real relations on real storages.
 *
 * The SQL is proven elsewhere. What only this level can show is whether a relation
 * declared on a storage document turns into the right join for the right tenant, and
 * whether reaching into another storage respects that storage's own rules - which is
 * the part a query builder has no opinion about.
 */
@DisplayName("Joins across storages, through the service")
class MySQLJoinOperationsIntegrationTest extends AbstractMySQLSpringIntegrationTest {



    private static final String ORDERS = "orders";
    private static final String CUSTOMERS = "customers";
    private static final String TAGS = "tags";

    private static DSLContext ctx;

    @Autowired
    private AppDataService appDataService;

    @BeforeEach
    void setUp() {

        Mockito.when(this.inheritanceService.order(Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenReturn(Mono.just(List.of(SYSTEM)));

        ctx = mysql();

        exec("DROP DATABASE IF EXISTS `" + SYSTEM + "_" + APP_CODE + "`");

        this.givenStorages(null);
        this.givenConnection();
    }

    private static void exec(String sql) {
        Mono.from(ctx.query(sql)).block();
    }

    // ---------------------------------------------------------------- fixtures

    /** @param customerReadAuth put on the JOINED storage, not the one being read */
    private void givenStorages(String customerReadAuth) {

        this.mongoTemplate.remove(new org.springframework.data.mongodb.core.query.Query(), Storage.class)
                .block();

        Storage customers = new Storage();
        customers.setName(CUSTOMERS).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        customers.setUniqueName("testapp_customers");
        customers.setReadAuth(customerReadAuth);
        customers.setSchema(new HashMap<>(Map.of(
                "type",
                "OBJECT",
                "properties",
                new HashMap<>(Map.of(
                        "name", new HashMap<>(Map.of("type", "STRING", "maxLength", 40)),
                        "region", new HashMap<>(Map.of("type", "STRING", "maxLength", 20)))))));
        this.insertRaw(customers);

        Storage tags = new Storage();
        tags.setName(TAGS).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        tags.setUniqueName("testapp_tags");
        tags.setSchema(new HashMap<>(Map.of(
                "type",
                "OBJECT",
                "properties",
                new HashMap<>(Map.of("label", new HashMap<>(Map.of("type", "STRING", "maxLength", 20)))))));
        this.insertRaw(tags);

        Map<String, StorageRelation> relations = new LinkedHashMap<>();
        relations.put(
                "customer",
                new StorageRelation()
                        .setStorageName(CUSTOMERS)
                        .setRelationType(StorageRelationType.TO_ONE)
                        .setFieldName("_id"));
        relations.put(
                "labels",
                new StorageRelation()
                        .setStorageName(TAGS)
                        .setRelationType(StorageRelationType.TO_MANY)
                        .setFieldName("_id"));

        Storage orders = new Storage();
        orders.setName(ORDERS).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        orders.setUniqueName("testapp_orders");
        orders.setRelations(relations);
        // `customer` is NOT declared here, and must not be: StorageService.validate
        // refuses a storage whose relation key is also a schema property, and all 27
        // relation fields in the fleet are absent from their schemas. The column for
        // it comes from the relation map, which is the whole point of this fixture.
        orders.setSchema(new HashMap<>(Map.of(
                "type",
                "OBJECT",
                "properties",
                new HashMap<>(Map.of("amount", new HashMap<>(Map.of("type", "DOUBLE")))))));
        this.insertRaw(orders);
    }

    private void givenConnection() {

        Connection conn = new Connection();
        conn.setConnectionType(ConnectionType.APP_DATA)
                .setConnectionSubType(ConnectionSubType.MYSQL)
                .setIsAppLevel(Boolean.TRUE)
                .setConnectionDetails(new HashMap<>(Map.of(
                        "url",
                        mysqlUrl(),
                        "username",
                        "root",
                        "password",
                        "test")));
        conn.setName("appData").setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        conn.setId("0000000000000000joindata");

        this.insertRaw(conn);
    }

    private <T> T asClient(Mono<T> mono, String... extraAuthorities) {

        String[] base = allAuthoritiesFor("Storage");
        String[] all = new String[base.length + extraAuthorities.length];
        System.arraycopy(base, 0, all, 0, base.length);
        System.arraycopy(extraAuthorities, 0, all, base.length, extraAuthorities.length);

        ContextAuthentication ca = this.authFor(SYSTEM, all);
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca)).block();
    }

    private String writeCustomer(String name, String region) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("region", region);
        return String.valueOf(this.asClient(this.appDataService.create(
                        APP_CODE, SYSTEM, CUSTOMERS, new DataObject().setData(row), false, null))
                .get("_id"));
    }

    private void writeOrder(double amount, String customerId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("amount", amount);
        row.put("customer", customerId);
        this.asClient(this.appDataService.create(
                APP_CODE, SYSTEM, ORDERS, new DataObject().setData(row), false, null));
    }

    private void givenRows() {
        String asha = writeCustomer("Asha", "north");
        String bo = writeCustomer("Bo", "south");

        writeOrder(100, asha);
        writeOrder(200, asha);
        writeOrder(50, bo);
        writeOrder(7, null);
    }

    private static StorageJoin join(JoinType type) {
        return new StorageJoin().setRelation("customer").setType(type);
    }

    // ---------------------------------------------------------------- tests

    @Nested
    @DisplayName("the relation column itself")
    class RelationColumn {

        @Test
        @Timeout(300)
        @DisplayName("a relation gets a real column, even though the schema may not declare it")
        void relationHasAColumn() {
            givenRows();

            // Before this, every relation-bearing storage failed its first write with
            // "Unknown column 'customer' in 'field list'", because the column came
            // from the schema and the schema is forbidden from mentioning it.
            Map<String, String> types = reactor.core.publisher.Flux.from(ctx.resultQuery(
                            "SELECT COLUMN_NAME, COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='"
                                    + SYSTEM + "_" + APP_CODE + "' AND TABLE_NAME='testapp_orders'"))
                    .collectMap(r -> String.valueOf(r.get(0)), r -> String.valueOf(r.get(1)))
                    .block();

            assertEquals("char(26)", types.get("customer"), String.valueOf(types));
            assertEquals("char(26)", types.get("_id"), "the same width as the key it points at");
        }

        @Test
        @Timeout(300)
        @DisplayName("a to-many relation is a JSON column, which is why it cannot be joined")
        void toManyIsJson() {
            givenRows();

            String type = reactor.core.publisher.Mono.from(ctx.resultQuery(
                            "SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='" + SYSTEM
                                    + "_" + APP_CODE + "' AND TABLE_NAME='testapp_orders'"
                                    + " AND COLUMN_NAME='labels'"))
                    .map(r -> String.valueOf(r.get(0)))
                    .block();

            // A list of ids in one column. The refusal to join through it is not an
            // implementation gap, it is this.
            assertEquals("json", type);
        }

        @Test
        @Timeout(300)
        @DisplayName("the relation value round trips")
        void valueRoundTrips() {
            String asha = writeCustomer("Asha", "north");
            writeOrder(100, asha);

            Page<Map<String, Object>> page =
                    asClient(appDataService.readPage(APP_CODE, SYSTEM, ORDERS, new Query()));

            assertEquals(asha, page.getContent().get(0).get("customer"));
        }
    }

    @Nested
    @DisplayName("reading")
    class Reading {

        @Test
        @Timeout(300)
        @DisplayName("the related row arrives nested, from one query")
        void nested() {
            givenRows();

            Page<Map<String, Object>> page = asClient(appDataService.readPage(
                    APP_CODE, SYSTEM, ORDERS,
                    new Query().setJoins(List.of(join(JoinType.INNER)))));

            assertEquals(3, page.getContent().size());
            Object customer = page.getContent().get(0).get("customer");
            assertTrue(customer instanceof Map, String.valueOf(customer));
            assertNotNull(((Map<?, ?>) customer).get("region"));
        }

        @Test
        @Timeout(300)
        @DisplayName("the page can be narrowed by a column of the other storage")
        void filterAcross() {
            givenRows();

            Page<Map<String, Object>> page = asClient(appDataService.readPage(
                    APP_CODE, SYSTEM, ORDERS,
                    new Query()
                            .setJoins(List.of(join(JoinType.INNER)))
                            .setCondition(FilterCondition.make("customer.region", "north"))
                            .setCount(true)));

            // The eager fetch expands a relation after the page has been chosen, so
            // it can never narrow the page. This is the whole difference.
            assertEquals(2, page.getContent().size());
            assertEquals(2L, page.getTotalElements(), "the count has to agree with the page");
        }

        @Test
        @Timeout(300)
        @DisplayName("a LEFT join keeps the order with no customer")
        void leftKeepsOrphans() {
            givenRows();

            Page<Map<String, Object>> page = asClient(appDataService.readPage(
                    APP_CODE, SYSTEM, ORDERS, new Query().setJoins(List.of(join(JoinType.LEFT)))));

            assertEquals(4, page.getContent().size());
        }

        @Test
        @Timeout(300)
        @DisplayName("sorting by a column of the other storage works")
        void sortAcross() {
            givenRows();

            Page<Map<String, Object>> page = asClient(appDataService.readPage(
                    APP_CODE, SYSTEM, ORDERS,
                    new Query()
                            .setJoins(List.of(join(JoinType.INNER)))
                            .setSort(org.springframework.data.domain.Sort.by(
                                    org.springframework.data.domain.Sort.Order.asc("customer.region")))));

            assertEquals("north", ((Map<?, ?>) page.getContent().get(0).get("customer")).get("region"));
        }

        @Test
        @Timeout(300)
        @DisplayName("a read with no joins is unchanged")
        void noJoinsUnchanged() {
            givenRows();

            Page<Map<String, Object>> page =
                    asClient(appDataService.readPage(APP_CODE, SYSTEM, ORDERS, new Query()));

            assertEquals(4, page.getContent().size());
            // The relation column is still the raw id when nothing asked for a join.
            Object customer = page.getContent().get(0).get("customer");
            assertFalse(customer instanceof Map, String.valueOf(customer));
        }
    }

    @Nested
    @DisplayName("aggregating")
    class Aggregating {

        @Test
        @Timeout(300)
        @DisplayName("revenue by a region that lives in another storage")
        void revenueByJoinedRegion() {
            givenRows();

            Page<Map<String, Object>> page = asClient(appDataService.aggregate(
                    APP_CODE, SYSTEM, ORDERS,
                    new AggregateQuery()
                            .setJoins(List.of(join(JoinType.INNER)))
                            .setGroupBy(List.of(
                                    new GroupByField().setField("customer.region").setAlias("region")))
                            .setAggregations(List.of(new Aggregation()
                                    .setFunction(AggregateFunction.SUM)
                                    .setField("amount")
                                    .setAlias("total")))
                            .setCount(Boolean.TRUE)));

            assertEquals(2, page.getContent().size());
            assertEquals(2L, page.getTotalElements());

            Map<String, Double> byRegion = new LinkedHashMap<>();
            page.getContent()
                    .forEach(r -> byRegion.put(
                            String.valueOf(r.get("region")), ((Number) r.get("total")).doubleValue()));

            assertEquals(300d, byRegion.get("north"));
            assertEquals(50d, byRegion.get("south"));
        }

        @Test
        @Timeout(300)
        @DisplayName("a measure on the joined side is allowed when its column is numeric there")
        void measureAcross() {
            givenRows();

            Page<Map<String, Object>> page = asClient(appDataService.aggregate(
                    APP_CODE, SYSTEM, ORDERS,
                    new AggregateQuery()
                            .setJoins(List.of(join(JoinType.INNER)))
                            .setAggregations(List.of(new Aggregation()
                                    .setFunction(AggregateFunction.COUNT)
                                    .setField("customer.name")
                                    .setAlias("named")))));

            assertEquals(3L, ((Number) page.getContent().get(0).get("named")).longValue());
        }
    }

    @Nested
    @DisplayName("subqueries, the direction a join cannot go")
    class SubQueries {

        private StorageSubQuery orders(String alias) {
            return new StorageSubQuery()
                    .setStorage(ORDERS)
                    .setRelation("customer")
                    .setAlias(alias);
        }

        @Test
        @Timeout(300)
        @DisplayName("customers with more than one order")
        void customersWithMoreThanOneOrder() {
            givenRows();

            // Inexpressible before this. A relation is declared on the side holding
            // the id, so a join can only walk towards the parent: an order knows its
            // customer, a customer knows nothing of its orders.
            Page<Map<String, Object>> page = asClient(appDataService.readPage(
                    APP_CODE, SYSTEM, CUSTOMERS,
                    new Query()
                            .setSubQueries(List.of(orders("orders")
                                    .setHaving(new FilterCondition()
                                            .setField("count")
                                            .setValue(1)
                                            .setOperator(com.fincity.saas.commons.model.condition
                                                    .FilterConditionOperator.GREATER_THAN))))
                            .setCount(true)));

            assertEquals(1, page.getContent().size(), "only Asha has two");
            assertEquals("Asha", page.getContent().get(0).get("name"));
            assertEquals(1L, page.getTotalElements(), "the count agrees with the page");
        }

        @Test
        @Timeout(300)
        @DisplayName("the measure comes back, so it can be read as well as filtered on")
        void measureIsReturned() {
            givenRows();

            Page<Map<String, Object>> page = asClient(appDataService.readPage(
                    APP_CODE, SYSTEM, CUSTOMERS, new Query().setSubQueries(List.of(orders("orders")))));

            Map<String, Object> asha = page.getContent().stream()
                    .filter(r -> "Asha".equals(r.get("name")))
                    .findFirst()
                    .orElseThrow();

            assertTrue(asha.get("orders") instanceof Map, String.valueOf(asha.get("orders")));
            assertEquals(2L, ((Number) ((Map<?, ?>) asha.get("orders")).get("count")).longValue());
        }

        @Test
        @Timeout(300)
        @DisplayName("one row per parent, so paging stays honest")
        void oneRowPerParent() {
            givenRows();

            Page<Map<String, Object>> page = asClient(appDataService.readPage(
                    APP_CODE, SYSTEM, CUSTOMERS,
                    new Query().setSubQueries(List.of(orders("orders"))).setCount(true)));

            // A join to the many side would return three rows for two customers,
            // because Asha has two orders. LIMIT would then count duplicates and the
            // page size would be a lie.
            assertEquals(2, page.getContent().size());
            assertEquals(2L, page.getTotalElements());
        }

        @Test
        @Timeout(300)
        @DisplayName("required false keeps the customers who have never ordered")
        void optionalKeepsChildless() {
            writeCustomer("Dee", "west");
            givenRows();

            Page<Map<String, Object>> required = asClient(appDataService.readPage(
                    APP_CODE, SYSTEM, CUSTOMERS, new Query().setSubQueries(List.of(orders("orders")))));

            Page<Map<String, Object>> optional = asClient(appDataService.readPage(
                    APP_CODE, SYSTEM, CUSTOMERS,
                    new Query().setSubQueries(List.of(orders("orders").setRequired(Boolean.FALSE)))));

            assertEquals(2, required.getContent().size(), "Dee has no orders");
            assertEquals(3, optional.getContent().size(), "and is kept when the subquery is optional");

            Map<String, Object> dee = optional.getContent().stream()
                    .filter(r -> "Dee".equals(r.get("name")))
                    .findFirst()
                    .orElseThrow();

            // The key stays with a null measure. Dropping it would make "none" and
            // "not asked" indistinguishable.
            assertTrue(dee.containsKey("orders"));
            assertEquals(null, ((Map<?, ?>) dee.get("orders")).get("count"));
        }

        @Test
        @Timeout(300)
        @DisplayName("the children can be filtered before they are counted")
        void conditionOnTheChildren() {
            givenRows();

            Page<Map<String, Object>> page = asClient(appDataService.readPage(
                    APP_CODE, SYSTEM, CUSTOMERS,
                    new Query().setSubQueries(List.of(orders("big")
                            .setCondition(new FilterCondition()
                                    .setField("amount")
                                    .setValue(150)
                                    .setOperator(com.fincity.saas.commons.model.condition
                                            .FilterConditionOperator.GREATER_THAN))))));

            // Only Asha's 200 clears the bar, so only Asha has a big order at all.
            assertEquals(1, page.getContent().size());
            assertEquals("Asha", page.getContent().get(0).get("name"));
        }

        @Test
        @Timeout(300)
        @DisplayName("a measure other than count, summed over the children")
        void sumOverChildren() {
            givenRows();

            Page<Map<String, Object>> page = asClient(appDataService.readPage(
                    APP_CODE, SYSTEM, CUSTOMERS,
                    new Query().setSubQueries(List.of(orders("orders")
                            .setAggregations(List.of(new Aggregation()
                                    .setFunction(AggregateFunction.SUM)
                                    .setField("amount")
                                    .setAlias("total")))))));

            Map<String, Object> asha = page.getContent().stream()
                    .filter(r -> "Asha".equals(r.get("name")))
                    .findFirst()
                    .orElseThrow();

            assertEquals(300d, ((Number) ((Map<?, ?>) asha.get("orders")).get("total")).doubleValue());
        }

        @Test
        @Timeout(300)
        @DisplayName("customers sorted by how many orders they have")
        void sortByChildCount() {
            givenRows();

            Page<Map<String, Object>> page = asClient(appDataService.readPage(
                    APP_CODE, SYSTEM, CUSTOMERS,
                    new Query()
                            .setSubQueries(List.of(orders("orders")))
                            .setSort(org.springframework.data.domain.Sort.by(
                                    org.springframework.data.domain.Sort.Order.desc("orders.count")))));

            assertEquals("Asha", page.getContent().get(0).get("name"));
            assertEquals("Bo", page.getContent().get(1).get("name"));
        }

        @Test
        @Timeout(300)
        @DisplayName("a subquery measure can be grouped and summed by an aggregate")
        void aggregateOverSubQuery() {
            givenRows();

            Page<Map<String, Object>> page = asClient(appDataService.aggregate(
                    APP_CODE, SYSTEM, CUSTOMERS,
                    new AggregateQuery()
                            .setSubQueries(List.of(orders("orders")))
                            .setGroupBy(List.of(new GroupByField().setField("region")))
                            .setAggregations(List.of(new Aggregation()
                                    .setFunction(AggregateFunction.SUM)
                                    .setField("orders.count")
                                    .setAlias("placed")))));

            Map<String, Double> byRegion = new LinkedHashMap<>();
            page.getContent()
                    .forEach(r -> byRegion.put(
                            String.valueOf(r.get("region")), ((Number) r.get("placed")).doubleValue()));

            assertEquals(2d, byRegion.get("north"), "Asha's two");
            assertEquals(1d, byRegion.get("south"), "Bo's one");
        }

        @Test
        @Timeout(300)
        @DisplayName("a relation pointing at a different storage is refused")
        void wrongTargetRefused() {
            givenRows();

            // `labels` points at tags, not at customers. Counting the wrong table
            // would answer a question nobody asked, plausibly.
            GenericException e = assertThrows(
                    GenericException.class,
                    () -> asClient(appDataService.readPage(
                            APP_CODE, SYSTEM, CUSTOMERS,
                            new Query().setSubQueries(List.of(new StorageSubQuery()
                                    .setStorage(ORDERS)
                                    .setRelation("labels")
                                    .setAlias("x"))))));

            assertTrue(e.getMessage().contains("points at"), e.getMessage());
        }

        @Test
        @Timeout(300)
        @DisplayName("the child storage's own readAuth is enforced")
        void childReadAuth() {
            givenStorages("Authorities.CUSTOMER_READ");
            givenRows();

            // Counting another storage's rows is still reading them.
            GenericException e = assertThrows(
                    GenericException.class,
                    () -> asClient(appDataService.readPage(
                            APP_CODE, SYSTEM, ORDERS,
                            new Query().setSubQueries(List.of(new StorageSubQuery()
                                    .setStorage(ORDERS)
                                    .setRelation("customer")
                                    .setAlias("self"))))));

            assertTrue(e.getMessage().contains("points at") || e.getStatusCode().value() == 403, e.getMessage());
        }
    }

    @Nested
    @DisplayName("what is refused")
    class Refusals {

        @Test
        @Timeout(300)
        @DisplayName("a TO_MANY relation is refused with the reason")
        void toManyRefused() {
            givenRows();

            GenericException e = assertThrows(
                    GenericException.class,
                    () -> asClient(appDataService.readPage(
                            APP_CODE, SYSTEM, ORDERS,
                            new Query().setJoins(List.of(new StorageJoin().setRelation("labels"))))));

            assertTrue(e.getMessage().contains("cannot use an index"), e.getMessage());
        }

        @Test
        @Timeout(300)
        @DisplayName("a relation the storage does not declare is refused")
        void unknownRelation() {
            givenRows();

            GenericException e = assertThrows(
                    GenericException.class,
                    () -> asClient(appDataService.readPage(
                            APP_CODE, SYSTEM, ORDERS,
                            new Query().setJoins(List.of(new StorageJoin().setRelation("supplier"))))));

            assertTrue(e.getMessage().contains("no relation"), e.getMessage());
        }

        @Test
        @Timeout(300)
        @DisplayName("a delete with joins is refused rather than deleting by an unapplied filter")
        void deleteWithJoins() {
            givenRows();

            GenericException e = assertThrows(
                    GenericException.class,
                    () -> asClient(appDataService.deleteByFilter(
                            APP_CODE,
                            SYSTEM,
                            ORDERS,
                            new Query()
                                    .setJoins(List.of(join(JoinType.INNER)))
                                    .setCondition(FilterCondition.make("customer.region", "north")),
                            Boolean.FALSE,
                            null)));

            assertEquals(501, e.getStatusCode().value(), e.getMessage());

            // And nothing went.
            assertEquals(
                    4,
                    asClient(appDataService.readPage(APP_CODE, SYSTEM, ORDERS, new Query()))
                            .getContent()
                            .size());
        }

        @Test
        @Timeout(300)
        @DisplayName("the joined storage's own readAuth is enforced")
        void joinedStorageReadAuth() {
            givenStorages("Authorities.CUSTOMER_READ");
            givenRows();

            // The caller may read orders. That is not a grant to read customers, and
            // a join reads far more of the other storage than the eager fetch does.
            GenericException e = assertThrows(
                    GenericException.class,
                    () -> asClient(appDataService.readPage(
                            APP_CODE, SYSTEM, ORDERS, new Query().setJoins(List.of(join(JoinType.INNER))))));

            assertEquals(403, e.getStatusCode().value(), e.getMessage());
        }

        @Test
        @Timeout(300)
        @DisplayName("and is satisfied by the right authority")
        void joinedStorageReadAuthGranted() {
            givenStorages("Authorities.CUSTOMER_READ");
            givenRows();

            Page<Map<String, Object>> page = asClient(
                    appDataService.readPage(
                            APP_CODE, SYSTEM, ORDERS, new Query().setJoins(List.of(join(JoinType.INNER)))),
                    "Authorities.CUSTOMER_READ");

            assertEquals(3, page.getContent().size());
        }
    }
}
