package com.fincity.saas.core.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.exeception.GenericException;

import com.fincity.saas.commons.core.document.Connection;
import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.enums.ConnectionSubType;
import com.fincity.saas.commons.core.enums.ConnectionType;
import com.fincity.saas.commons.core.enums.StorageRelationConstraint;
import com.fincity.saas.commons.core.enums.StorageRelationType;
import com.fincity.saas.commons.core.model.DataObject;
import com.fincity.saas.commons.core.model.StorageRelation;
import com.fincity.saas.commons.core.service.connection.appdata.AppDataService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Delete constraints, enforced by whichever of the two can actually do it.
 *
 * Everything here turns on a direction that was wrong until now. A constraint is
 * declared on the relation that HOLDS the reference - {@code orders.customer ->
 * customers} - and in SQL it protects the referenced row: deleting a CUSTOMER is what
 * RESTRICT refuses and what CASCADE propagates from. The service used to read it the
 * other way round and act on the row the relation pointed at, which is not what
 * either word means and is not something a foreign key could ever express.
 *
 * Nothing in the fleet moved as a result: all 27 live relations declare NOTHING, so
 * the first storage to mean either word gets the meaning it expects.
 */
@DisplayName("Delete constraints, in the database where possible")
class MySQLRelationConstraintIntegrationTest extends AbstractMySQLSpringIntegrationTest {

    private static final String ORDERS = "orders";
    private static final String CUSTOMERS = "customers";
    private static final String TAGS = "tags";

    private static final String TENANT = SYSTEM + "_" + APP_CODE;

    private static DSLContext ctx;

    @Autowired
    private AppDataService appDataService;

    @BeforeEach
    void setUp() {

        Mockito.when(this.inheritanceService.order(Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenReturn(Mono.just(List.of(SYSTEM)));

        ctx = mysql();
        exec("DROP DATABASE IF EXISTS `" + TENANT + "`");
        this.givenConnection();
    }

    private static void exec(String sql) {
        Mono.from(ctx.query(sql)).block();
    }

    private static long count(String sql) {
        Object v = Mono.from(ctx.resultQuery(sql)).map(r -> r.get(0)).block();
        return v instanceof Number n ? n.longValue() : 0L;
    }

    // ---------------------------------------------------------------- fixtures

    /** The DELETE_RULE MySQL reports for the key on orders.customer, or null if there is none. */
    private static String deleteRule() {
        return Flux.from(ctx.resultQuery("SELECT r.DELETE_RULE FROM information_schema.REFERENTIAL_CONSTRAINTS r"
                        + " JOIN information_schema.KEY_COLUMN_USAGE k ON k.CONSTRAINT_NAME = r.CONSTRAINT_NAME"
                        + " AND k.CONSTRAINT_SCHEMA = r.CONSTRAINT_SCHEMA"
                        + " WHERE r.CONSTRAINT_SCHEMA = '" + TENANT + "' AND k.TABLE_NAME = 'testapp_orders'"
                        + " AND k.COLUMN_NAME = 'customer'"))
                .map(r -> String.valueOf(r.get(0)))
                .next()
                .block();
    }

    private void givenStorages(
            StorageRelationConstraint onCustomer, StorageRelationConstraint onTags, boolean ordersVersioned) {
        this.givenStorages(onCustomer, onTags, ordersVersioned, false);
    }

    private void givenStorages(
            StorageRelationConstraint onCustomer,
            StorageRelationConstraint onTags,
            boolean ordersVersioned,
            boolean customersVersioned) {

        this.mongoTemplate
                .remove(new org.springframework.data.mongodb.core.query.Query(), Storage.class)
                .block();

        Storage customers = new Storage();
        customers.setName(CUSTOMERS).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        customers.setUniqueName("testapp_customers");
        customers.setIsVersioned(customersVersioned);
        customers.setSchema(new HashMap<>(Map.of(
                "type", "OBJECT",
                "properties", new HashMap<>(Map.of("name", new HashMap<>(Map.of("type", "STRING", "maxLength", 40)))))));
        this.insertRaw(customers);

        Storage tags = new Storage();
        tags.setName(TAGS).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        tags.setUniqueName("testapp_tags");
        tags.setSchema(new HashMap<>(Map.of(
                "type", "OBJECT",
                "properties", new HashMap<>(Map.of("label", new HashMap<>(Map.of("type", "STRING", "maxLength", 20)))))));
        this.insertRaw(tags);

        Map<String, StorageRelation> relations = new LinkedHashMap<>();
        relations.put(
                "customer",
                new StorageRelation()
                        .setStorageName(CUSTOMERS)
                        .setRelationType(StorageRelationType.TO_ONE)
                        .setFieldName("_id")
                        .setDeleteConstraint(onCustomer));
        relations.put(
                "labels",
                new StorageRelation()
                        .setStorageName(TAGS)
                        .setRelationType(StorageRelationType.TO_MANY)
                        .setFieldName("_id")
                        .setDeleteConstraint(onTags));

        Storage orders = new Storage();
        orders.setName(ORDERS).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        orders.setUniqueName("testapp_orders");
        orders.setRelations(relations);
        orders.setIsVersioned(ordersVersioned);
        orders.setSchema(new HashMap<>(Map.of(
                "type", "OBJECT",
                "properties", new HashMap<>(Map.of("amount", new HashMap<>(Map.of("type", "DOUBLE")))))));
        this.insertRaw(orders);
    }

    /** Orders versioned, so the cascade runs here, and gated behind an authority nobody holds. */
    private void givenStoragesWithChildDeleteAuth() {

        givenStorages(StorageRelationConstraint.CASCADE, StorageRelationConstraint.NOTHING, true);

        Storage orders = this.mongoTemplate
                .findOne(
                        org.springframework.data.mongodb.core.query.Query.query(
                                org.springframework.data.mongodb.core.query.Criteria.where("name")
                                        .is(ORDERS)),
                        Storage.class)
                .block();

        orders.setDeleteAuth("Authorities.ORDERS.ROLE_Nobody");
        this.mongoTemplate.save(orders).block();
        this.cacheService.evictAllCaches().block();
    }

    private void givenConnection() {

        Connection conn = new Connection();
        conn.setConnectionType(ConnectionType.APP_DATA)
                .setConnectionSubType(ConnectionSubType.MYSQL)
                .setIsAppLevel(Boolean.TRUE)
                .setConnectionDetails(new HashMap<>(Map.of(
                        "url", mysqlUrl(), "username", "root", "password", "test")));
        conn.setName("appData").setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        conn.setId("00000000000000constraint");

        this.insertRaw(conn);
    }

    private <T> T asClient(Mono<T> mono) {
        ContextAuthentication ca = this.authFor(SYSTEM, allAuthoritiesFor("Storage"));
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca)).block();
    }

    private String writeCustomer(String name) {
        return String.valueOf(this.asClient(this.appDataService.create(
                        APP_CODE,
                        SYSTEM,
                        CUSTOMERS,
                        new DataObject().setData(new LinkedHashMap<>(Map.of("name", name))),
                        false,
                        null))
                .get("_id"));
    }

    private String writeTag(String label) {
        return String.valueOf(this.asClient(this.appDataService.create(
                        APP_CODE,
                        SYSTEM,
                        TAGS,
                        new DataObject().setData(new LinkedHashMap<>(Map.of("label", label))),
                        false,
                        null))
                .get("_id"));
    }

    private void writeOrder(double amount, String customerId, List<String> tagIds) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("amount", amount);
        if (customerId != null) row.put("customer", customerId);
        if (tagIds != null) row.put("labels", tagIds);
        this.asClient(this.appDataService.create(
                APP_CODE, SYSTEM, ORDERS, new DataObject().setData(row), false, null));
    }

    private boolean deleteCustomer(String id) {
        return Boolean.TRUE.equals(this.asClient(this.appDataService.delete(APP_CODE, SYSTEM, CUSTOMERS, id, null)));
    }

    // ---------------------------------------------------------------- tests

    @Nested
    @DisplayName("NOTHING")
    class Nothing {

        @Test
        @Timeout(300)
        @DisplayName("creates no key at all, so every relation that exists today is untouched")
        void noKey() {
            // Mapping NOTHING onto MySQL's NO ACTION would have been the obvious
            // reading and would also have been wrong: NO ACTION is a synonym for
            // RESTRICT, so all 27 live relations would have started refusing deletes
            // the first time anything published.
            givenStorages(StorageRelationConstraint.NOTHING, StorageRelationConstraint.NOTHING, false);

            String asha = writeCustomer("Asha");
            writeOrder(100, asha, null);

            assertEquals(null, deleteRule());
        }

        @Test
        @Timeout(300)
        @DisplayName("lets a referenced row go, exactly as before")
        void deleteAllowed() {
            givenStorages(StorageRelationConstraint.NOTHING, StorageRelationConstraint.NOTHING, false);

            String asha = writeCustomer("Asha");
            writeOrder(100, asha, null);

            assertTrue(deleteCustomer(asha));
            assertEquals(1, count("SELECT COUNT(*) FROM `" + TENANT + "`.`testapp_orders`"));
        }
    }

    @Nested
    @DisplayName("RESTRICT")
    class Restrict {

        @Test
        @Timeout(300)
        @DisplayName("becomes a real foreign key, not a loop in the service")
        void realKey() {
            givenStorages(StorageRelationConstraint.RESTRICT, StorageRelationConstraint.NOTHING, false);

            String asha = writeCustomer("Asha");
            writeOrder(100, asha, null);

            assertEquals("RESTRICT", deleteRule());
        }

        @Test
        @Timeout(300)
        @DisplayName("refuses to delete a customer that orders point at")
        void refusesTheDelete() {
            givenStorages(StorageRelationConstraint.RESTRICT, StorageRelationConstraint.NOTHING, false);

            String asha = writeCustomer("Asha");
            writeOrder(100, asha, null);

            assertThrows(Exception.class, () -> deleteCustomer(asha));
            assertEquals(1, count("SELECT COUNT(*) FROM `" + TENANT + "`.`testapp_customers`"));
        }

        @Test
        @Timeout(300)
        @DisplayName("allows a customer nothing points at")
        void allowsUnreferenced() {
            givenStorages(StorageRelationConstraint.RESTRICT, StorageRelationConstraint.NOTHING, false);

            String asha = writeCustomer("Asha");
            String bo = writeCustomer("Bo");
            writeOrder(100, asha, null);

            assertTrue(deleteCustomer(bo));
        }

        @Test
        @Timeout(300)
        @DisplayName("holds even for a write that never went through the service")
        void holdsAgainstRawSql() {
            // The whole reason this belongs in the database. The service loop reads,
            // decides and then deletes with no lock across the three, so anything
            // writing to the tenant schema directly - a migration, a fix-up script,
            // another process - walks straight past it.
            givenStorages(StorageRelationConstraint.RESTRICT, StorageRelationConstraint.NOTHING, false);

            String asha = writeCustomer("Asha");
            writeOrder(100, asha, null);

            assertThrows(
                    Exception.class,
                    () -> exec("DELETE FROM `" + TENANT + "`.`testapp_customers` WHERE `_id` = '" + asha + "'"));
        }
    }

    @Nested
    @DisplayName("CASCADE")
    class Cascade {

        @Test
        @Timeout(300)
        @DisplayName("deleting the customer takes its orders with it")
        void cascades() {
            givenStorages(StorageRelationConstraint.CASCADE, StorageRelationConstraint.NOTHING, false);

            String asha = writeCustomer("Asha");
            String bo = writeCustomer("Bo");
            writeOrder(100, asha, null);
            writeOrder(200, asha, null);
            writeOrder(50, bo, null);

            assertTrue(deleteCustomer(asha));

            assertEquals(1, count("SELECT COUNT(*) FROM `" + TENANT + "`.`testapp_orders`"));
        }

        @Test
        @Timeout(300)
        @DisplayName("goes to the database when deleting the child owes nothing")
        void inTheDatabase() {
            givenStorages(StorageRelationConstraint.CASCADE, StorageRelationConstraint.NOTHING, false);

            writeOrder(100, writeCustomer("Asha"), null);

            assertEquals("CASCADE", deleteRule());
        }

        @Test
        @Timeout(300)
        @DisplayName("is refused whole when the caller may not delete from the child")
        void cascadeIsAllOrNothing() {
            // Checked only as each delete ran, a caller allowed to remove some
            // children and not others would take the first lot out and then be
            // refused, leaving a half-cascaded parent. The database path cannot ask
            // this question at all, which is part of why a cascade over a versioned
            // child stays in the service.
            givenStoragesWithChildDeleteAuth();

            String asha = writeCustomer("Asha");
            writeOrder(100, asha, null);

            assertThrows(Exception.class, () -> deleteCustomer(asha));

            assertEquals(1, count("SELECT COUNT(*) FROM `" + TENANT + "`.`testapp_orders`"));
            assertEquals(1, count("SELECT COUNT(*) FROM `" + TENANT + "`.`testapp_customers`"));
        }

        @Test
        @Timeout(300)
        @DisplayName("stays in the service when the child is versioned, and still cascades")
        void versionedChildStaysInTheService() {
            // A row deleted inside MySQL raises no event, runs no AFTER_DELETE
            // trigger and leaves its version rows behind. 39 storages are versioned,
            // so handing every cascade to the database would quietly drop all three
            // for a real fraction of them.
            givenStorages(StorageRelationConstraint.CASCADE, StorageRelationConstraint.NOTHING, true);

            String asha = writeCustomer("Asha");
            writeOrder(100, asha, null);
            writeOrder(200, asha, null);

            assertEquals(null, deleteRule());

            assertTrue(deleteCustomer(asha));
            assertEquals(0, count("SELECT COUNT(*) FROM `" + TENANT + "`.`testapp_orders`"));
        }
    }

    @Nested
    @DisplayName("TO_MANY, which no foreign key can express")
    class ToMany {

        @Test
        @Timeout(300)
        @DisplayName("gets no key, because the column is a JSON array")
        void noKey() {
            givenStorages(StorageRelationConstraint.NOTHING, StorageRelationConstraint.RESTRICT, false);

            writeOrder(100, null, List.of(writeTag("urgent")));

            long keys = count("SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE"
                    + " WHERE CONSTRAINT_SCHEMA = '" + TENANT + "' AND TABLE_NAME = 'testapp_orders'"
                    + " AND COLUMN_NAME = 'labels' AND REFERENCED_TABLE_NAME IS NOT NULL");

            assertEquals(0, keys);
        }

        @Test
        @Timeout(300)
        @DisplayName("the service refuses the delete instead, matching inside the JSON")
        void serviceRefuses() {
            // Written as a plain equality this would find nothing, and a RESTRICT
            // that counts zero allows exactly the delete it exists to refuse.
            givenStorages(StorageRelationConstraint.NOTHING, StorageRelationConstraint.RESTRICT, false);

            String urgent = writeTag("urgent");
            writeOrder(100, null, List.of(urgent));

            assertThrows(
                    Exception.class,
                    () -> this.asClientDelete(urgent));

            assertEquals(1, count("SELECT COUNT(*) FROM `" + TENANT + "`.`testapp_tags`"));
        }

        @Test
        @Timeout(300)
        @DisplayName("an unreferenced tag still goes")
        void unreferencedGoes() {
            givenStorages(StorageRelationConstraint.NOTHING, StorageRelationConstraint.RESTRICT, false);

            String urgent = writeTag("urgent");
            String spare = writeTag("spare");
            writeOrder(100, null, List.of(urgent));

            assertTrue(Boolean.TRUE.equals(this.asClientDelete(spare)));
        }

        private Boolean asClientDelete(String tagId) {
            return MySQLRelationConstraintIntegrationTest.this.asClient(
                    MySQLRelationConstraintIntegrationTest.this.appDataService.delete(
                            APP_CODE, SYSTEM, TAGS, tagId, null));
        }
    }

    @Nested
    @DisplayName("changing the declaration")
    class Changing {

        @Test
        @Timeout(300)
        @DisplayName("setting a constraint back to NOTHING takes the key off")
        void keyIsDropped() {
            // A key left behind would keep refusing deletes nobody is asking it to
            // refuse, and nothing in the definition would say why.
            givenStorages(StorageRelationConstraint.RESTRICT, StorageRelationConstraint.NOTHING, false);

            writeOrder(100, writeCustomer("Asha"), null);
            assertEquals("RESTRICT", deleteRule());

            givenStorages(StorageRelationConstraint.NOTHING, StorageRelationConstraint.NOTHING, false);
            this.cacheService().evictAllCaches().block();

            writeOrder(200, writeCustomer("Bo"), null);

            assertEquals(null, deleteRule());
        }

        @Test
        @Timeout(300)
        @DisplayName("tightening to RESTRICT puts one on")
        void keyIsAdded() {
            givenStorages(StorageRelationConstraint.NOTHING, StorageRelationConstraint.NOTHING, false);

            writeOrder(100, writeCustomer("Asha"), null);
            assertEquals(null, deleteRule());

            givenStorages(StorageRelationConstraint.RESTRICT, StorageRelationConstraint.NOTHING, false);
            this.cacheService().evictAllCaches().block();

            writeOrder(200, writeCustomer("Bo"), null);

            assertEquals("RESTRICT", deleteRule());
        }

        private com.fincity.saas.commons.service.CacheService cacheService() {
            return MySQLRelationConstraintIntegrationTest.this.cacheService;
        }
    }

    @Nested
    @DisplayName("what a refused delete leaves behind")
    class RefusedDelete {

        @Test
        @Timeout(300)
        @DisplayName("comes back as a bad request, not a server error carrying the constraint name")
        void cleanError() {
            givenStorages(StorageRelationConstraint.RESTRICT, StorageRelationConstraint.NOTHING, false, false);

            String asha = writeCustomer("Asha");
            writeOrder(100, asha, null);

            GenericException e = assertThrows(GenericException.class, () -> deleteCustomer(asha));

            assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
            // The driver message names the constraint, the schema and the table.
            assertFalse(String.valueOf(e.getMessage()).contains("fk_"), e.getMessage());
        }

        @Test
        @Timeout(300)
        @DisplayName("does not throw away the history of a row that is still there")
        void historyIsIntact() {
            // The version used to be written before the delete, which was fine while
            // a delete could not be refused. With RESTRICT it meant deleteVersion
            // wiped a surviving row's entire history for a delete that never
            // happened, and that is not recoverable.
            givenStorages(StorageRelationConstraint.RESTRICT, StorageRelationConstraint.NOTHING, false, true);

            String asha = writeCustomer("Asha");
            writeOrder(100, asha, null);

            long before = count("SELECT COUNT(*) FROM `" + TENANT + "`.`testapp_customers_version`");
            assertTrue(before > 0);

            assertThrows(Exception.class, () -> MySQLRelationConstraintIntegrationTest.this.asClient(
                    MySQLRelationConstraintIntegrationTest.this.appDataService.delete(
                            APP_CODE, SYSTEM, CUSTOMERS, asha, Boolean.TRUE)));

            assertEquals(
                    before, count("SELECT COUNT(*) FROM `" + TENANT + "`.`testapp_customers_version`"));
            assertEquals(1, count("SELECT COUNT(*) FROM `" + TENANT + "`.`testapp_customers`"));
        }

        @Test
        @Timeout(300)
        @DisplayName("does not record a DELETE version for a row that survived")
        void noPhantomVersion() {
            givenStorages(StorageRelationConstraint.RESTRICT, StorageRelationConstraint.NOTHING, false, true);

            String asha = writeCustomer("Asha");
            writeOrder(100, asha, null);

            assertThrows(Exception.class, () -> deleteCustomer(asha));

            assertEquals(
                    0,
                    count("SELECT COUNT(*) FROM `" + TENANT + "`.`testapp_customers_version`"
                            + " WHERE `operation` = 'DELETE'"));
        }
    }

    @Nested
    @DisplayName("a relation that points at its own storage")
    class SelfRelation {

        private static final String NODES = "nodes";

        /** Versioned, so the cascade runs in the service rather than inside MySQL. */
        private void givenNodes() {

            MySQLRelationConstraintIntegrationTest.this
                    .mongoTemplate
                    .remove(new org.springframework.data.mongodb.core.query.Query(), Storage.class)
                    .block();

            Storage nodes = new Storage();
            nodes.setName(NODES).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
            nodes.setUniqueName("testapp_nodes");
            nodes.setIsVersioned(Boolean.TRUE);
            nodes.setRelations(new LinkedHashMap<>(Map.of(
                    "parent",
                    new StorageRelation()
                            .setStorageName(NODES)
                            .setRelationType(StorageRelationType.TO_ONE)
                            .setFieldName("_id")
                            .setDeleteConstraint(StorageRelationConstraint.CASCADE))));
            nodes.setSchema(new HashMap<>(Map.of(
                    "type",
                    "OBJECT",
                    "properties",
                    new HashMap<>(Map.of("label", new HashMap<>(Map.of("type", "STRING", "maxLength", 20)))))));
            MySQLRelationConstraintIntegrationTest.this.insertRaw(nodes);
        }

        private String writeNode(String label, String parent) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("label", label);
            if (parent != null) row.put("parent", parent);
            return String.valueOf(MySQLRelationConstraintIntegrationTest.this
                    .asClient(MySQLRelationConstraintIntegrationTest.this.appDataService.create(
                            APP_CODE, SYSTEM, NODES, new DataObject().setData(row), false, null))
                    .get("_id"));
        }

        private String chain(int depth) {
            String id = writeNode("n0", null);
            for (int i = 1; i < depth; i++) id = writeNode("n" + i, id);
            return id;
        }

        @Test
        @Timeout(300)
        @DisplayName("a short tree cascades all the way down")
        void shallowChain() {
            // tportTree.parent is a real self-relation in the fleet, so this is the
            // shape rather than a contrived one.
            givenNodes();
            chain(4);

            String root = String.valueOf(Mono.from(ctx.resultQuery(
                            "SELECT `_id` FROM `" + TENANT + "`.`testapp_nodes` WHERE `parent` IS NULL"))
                    .map(r -> r.get(0))
                    .block());

            assertTrue(Boolean.TRUE.equals(MySQLRelationConstraintIntegrationTest.this.asClient(
                    MySQLRelationConstraintIntegrationTest.this.appDataService.delete(
                            APP_CODE, SYSTEM, NODES, root, null))));

            assertEquals(0, count("SELECT COUNT(*) FROM `" + TENANT + "`.`testapp_nodes`"));
        }

        @Test
        @Timeout(300)
        @DisplayName("a chain deeper than the guard stops rather than running out of stack")
        void deepChain() {
            // Two storages pointing at each other with CASCADE would walk forever,
            // and so would a cycle in the data. The guard counts NESTING, not
            // batches - counted per batch instead, an ordinary wide cascade would
            // abort claiming the relations were cyclic.
            givenNodes();
            chain(14);

            String root = String.valueOf(Mono.from(ctx.resultQuery(
                            "SELECT `_id` FROM `" + TENANT + "`.`testapp_nodes` WHERE `parent` IS NULL"))
                    .map(r -> r.get(0))
                    .block());

            assertThrows(
                    Exception.class,
                    () -> MySQLRelationConstraintIntegrationTest.this.asClient(
                            MySQLRelationConstraintIntegrationTest.this.appDataService.delete(
                                    APP_CODE, SYSTEM, NODES, root, null)));
        }
    }

    @Nested
    @DisplayName("when the key cannot be installed")
    class KeyBlocked {

        /** A RESTRICT declared over data that already breaks it. */
        private String givenOrphanedRows() {

            givenStorages(StorageRelationConstraint.NOTHING, StorageRelationConstraint.NOTHING, false);

            writeOrder(100, writeCustomer("Asha"), null);
            exec("UPDATE `" + TENANT + "`.`testapp_orders` SET `customer` = 'NOTAREALIDNOTAREALIDNOTARE'");

            givenStorages(StorageRelationConstraint.RESTRICT, StorageRelationConstraint.NOTHING, false);
            MySQLRelationConstraintIntegrationTest.this.cacheService.evictAllCaches().block();

            String bo = writeCustomer("Bo");
            writeOrder(200, bo, null);
            return bo;
        }

        @Test
        @Timeout(300)
        @DisplayName("the key is not added over rows that already break it")
        void keyIsNotAdded() {
            // MySQL refuses the whole ALTER on one dangling reference and names the
            // constraint rather than the row, so on a table with history the error
            // is useless. Counting first leaves the table working without the key
            // and says how many rows are wrong.
            givenOrphanedRows();

            assertEquals(null, deleteRule());
            assertFalse(count("SELECT COUNT(*) FROM `" + TENANT + "`.`testapp_orders`") == 0);
        }

        @Test
        @Timeout(300)
        @DisplayName("the service enforces it instead, rather than standing down for a key that is not there")
        void serviceTakesItBack() {
            // The whole reason the capability check asks the TABLE. Answered from
            // the definition - can this relation be a foreign key? - it would say
            // yes, the service would stand down, and the RESTRICT the author
            // declared would be enforced by nobody at all.
            String bo = givenOrphanedRows();

            assertThrows(Exception.class, () -> deleteCustomer(bo));
            assertEquals(2, count("SELECT COUNT(*) FROM `" + TENANT + "`.`testapp_customers`"));
        }

        @Test
        @Timeout(300)
        @DisplayName("and hands back once the orphans are cleared and the key goes on")
        void handsBackWhenInstalled() {
            givenOrphanedRows();

            exec("DELETE FROM `" + TENANT + "`.`testapp_orders`"
                    + " WHERE `customer` = 'NOTAREALIDNOTAREALIDNOTARE'");

            MySQLRelationConstraintIntegrationTest.this.cacheService.evictAllCaches().block();
            writeOrder(300, writeCustomer("Cal"), null);

            assertEquals("RESTRICT", deleteRule());
        }
    }
}
