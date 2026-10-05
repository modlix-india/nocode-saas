package com.fincity.saas.core.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.bson.BsonDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.enums.StorageRelationType;
import com.fincity.saas.commons.core.model.DataObject;
import com.fincity.saas.commons.core.model.StorageRelation;
import com.fincity.saas.commons.core.service.connection.appdata.AppDataService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.reactivestreams.client.MongoClient;

import reactor.core.publisher.Mono;

/**
 * The eager fetch, which is the one read path that never grew up.
 *
 * Joins and subqueries both resolve the target storage, check its {@code readAuth},
 * and reach it in one statement. Eager does none of the three: it calls the backend
 * directly, caps the result at a number nobody chose, and runs once per row of the
 * page. All three are visible from the outside, so all three can be pinned here.
 */
@DisplayName("Eager fetch")
@Import(EagerFetchIntegrationTest.CommandCounting.class)
class EagerFetchIntegrationTest extends AbstractIntegrationTest {

    private static final String ORDERS = "orders";
    private static final String CUSTOMERS = "customers";
    private static final String TAGS = "tags";

    private static final String ORDERS_COLLECTION = "testapp_system_orders";
    private static final String CUSTOMERS_COLLECTION = "testapp_system_customers";
    private static final String TAGS_COLLECTION = "testapp_system_tags";

    private static final String LIVE_DB = SYSTEM + "_" + APP_CODE;

    /** An authority in the app's own namespace, which no caller here is given. */
    private static final String SECRET = "Authorities.CUSTOMERS.ROLE_Secret";

    @Autowired
    private AppDataService appDataService;

    @Autowired
    private MongoClient mongoClient;

    /**
     * Counts find commands per collection.
     *
     * The N+1 claim is about how many times the database is asked, which is not
     * visible in the result and cannot be inferred from it. Registered through the
     * client settings so it sees every command the driver actually sends.
     */
    @TestConfiguration
    static class CommandCounting {

        static final Map<String, AtomicInteger> FINDS = new ConcurrentHashMap<>();

        static void reset() {
            FINDS.clear();
        }

        static int finds(String collection) {
            AtomicInteger n = FINDS.get(collection);
            return n == null ? 0 : n.get();
        }

        @Bean
        MongoClientSettingsBuilderCustomizer countFinds() {
            return builder -> builder.addCommandListener(new CommandListener() {
                @Override
                public void commandStarted(CommandStartedEvent event) {
                    if (!"find".equals(event.getCommandName())) return;

                    BsonDocument command = event.getCommand();
                    if (!command.containsKey("find")) return;

                    FINDS.computeIfAbsent(command.getString("find").getValue(), k -> new AtomicInteger())
                            .incrementAndGet();
                }
            });
        }
    }

    @BeforeEach
    @AfterEach
    void dropAppData() {
        Mono.from(this.mongoClient.getDatabase(LIVE_DB).drop()).block();
        this.cacheService.evictAllCaches().block();
        CommandCounting.reset();
    }

    // ---------------------------------------------------------------- fixtures

    private void givenStorages(String customerReadAuth) {

        Storage customers = new Storage();
        customers.setName(CUSTOMERS).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        customers.setUniqueName(CUSTOMERS_COLLECTION);
        customers.setReadAuth(customerReadAuth);
        customers.setSchema(objectSchema("name"));
        this.insertRaw(customers);

        Storage tags = new Storage();
        tags.setName(TAGS).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        tags.setUniqueName(TAGS_COLLECTION);
        tags.setSchema(objectSchema("label"));
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
        orders.setUniqueName(ORDERS_COLLECTION);
        orders.setRelations(relations);
        orders.setSchema(objectSchema("ref"));
        this.insertRaw(orders);
    }

    private static Map<String, Object> objectSchema(String field) {
        Map<String, Object> property = new HashMap<>();
        property.put("type", "STRING");

        Map<String, Object> properties = new HashMap<>();
        properties.put(field, property);

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "OBJECT");
        schema.put("properties", properties);
        return schema;
    }

    private <T> T asClient(Mono<T> mono) {
        ContextAuthentication ca = this.authFor(SYSTEM, allAuthoritiesFor("Storage"));
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca)).block();
    }

    private String write(String storage, String field, Object value) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(field, value);
        return String.valueOf(this.asClient(this.appDataService.create(
                        APP_CODE, SYSTEM, storage, new DataObject().setData(data), false, null))
                .get("_id"));
    }

    private String writeOrder(String ref, String customerId, List<String> tagIds) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ref", ref);
        if (customerId != null) data.put("customer", customerId);
        if (tagIds != null) data.put("labels", tagIds);
        return String.valueOf(this.asClient(this.appDataService.create(
                        APP_CODE, SYSTEM, ORDERS, new DataObject().setData(data), false, null))
                .get("_id"));
    }

    private Map<String, Object> readEager(String id) {
        return this.asClient(this.appDataService.read(APP_CODE, SYSTEM, ORDERS, id, Boolean.TRUE, null));
    }

    private List<Map<String, Object>> readPageEager(int size) {
        Query query = new Query().setPage(0).setSize(size);
        query.setEager(Boolean.TRUE);
        return this.asClient(
                this.appDataService.readPage(APP_CODE, SYSTEM, ORDERS, query).map(page -> page.getContent()));
    }

    // ---------------------------------------------------------------- tests

    @Nested
    @DisplayName("the target storage's own rules")
    class TargetRules {

        @Test
        @Timeout(300)
        @DisplayName("a readAuth the caller does not hold refuses the read, as a join would")
        void readAuthIsEnforced() {
            // Joins and subqueries both resolve the target and check this. Eager
            // calls the backend directly, so today it hands back rows from a
            // storage the caller is not allowed to read, and nothing says so.
            givenStorages(SECRET);

            String asha = write(CUSTOMERS, "name", "Asha");
            String order = writeOrder("A1", asha, null);

            GenericException e = assertThrows(GenericException.class, () -> readEager(order));
            assertTrue(
                    String.valueOf(e.getMessage()).toLowerCase().contains("forbidden")
                            || e.getStatusCode().is4xxClientError(),
                    String.valueOf(e.getMessage()));
        }

        @Test
        @Timeout(300)
        @DisplayName("a target with no readAuth still resolves")
        void openTargetStillWorks() {
            givenStorages(null);

            String asha = write(CUSTOMERS, "name", "Asha");
            String order = writeOrder("A1", asha, null);

            Map<String, Object> read = readEager(order);

            assertInstanceOf(Map.class, read.get("customer"));
            assertEquals("Asha", ((Map<?, ?>) read.get("customer")).get("name"));
        }
    }

    @Nested
    @DisplayName("how much it returns")
    class Completeness {

        @Test
        @Timeout(300)
        @DisplayName("sixty children come back as sixty, not as the first fifty")
        void noSilentCap() {
            // query.setSize(50) is applied to a lookup BY ID, so a row with more
            // than fifty children silently loses the rest. Nothing in the response
            // distinguishes that from a row that only had fifty.
            givenStorages(null);

            List<String> tagIds = new ArrayList<>();
            for (int i = 0; i < 60; i++) tagIds.add(write(TAGS, "label", "t" + i));

            String order = writeOrder("A1", null, tagIds);

            Object labels = readEager(order).get("labels");

            assertInstanceOf(List.class, labels);
            assertEquals(60, ((List<?>) labels).size());
        }

        @Test
        @Timeout(300)
        @DisplayName("the declared order of a TO_MANY is preserved")
        void orderPreserved() {
            // The existing implementation sorts the fetched children back into the
            // order the parent stored them. Batching must not lose that.
            givenStorages(null);

            List<String> tagIds = new ArrayList<>();
            for (int i = 0; i < 5; i++) tagIds.add(write(TAGS, "label", "t" + i));

            java.util.Collections.reverse(tagIds);
            String order = writeOrder("A1", null, tagIds);

            List<?> labels = (List<?>) readEager(order).get("labels");

            assertEquals(
                    List.of("t4", "t3", "t2", "t1", "t0"),
                    labels.stream().map(l -> ((Map<?, ?>) l).get("label")).toList());
        }
    }

    @Nested
    @DisplayName("how many times it asks")
    class QueryCount {

        @Test
        @Timeout(300)
        @DisplayName("one page is one query per eager field, not one per row")
        void notOncePerRow() {
            // fillRelatedObjects runs per row of the page, so ten orders with one
            // eager relation is ten lookups against customers. This is the only
            // claim here that cannot be seen in the result, which is why the
            // driver is counted rather than the output.
            givenStorages(null);

            String asha = write(CUSTOMERS, "name", "Asha");
            String bo = write(CUSTOMERS, "name", "Bo");
            for (int i = 0; i < 10; i++) writeOrder("A" + i, i % 2 == 0 ? asha : bo, null);

            CommandCounting.reset();
            List<Map<String, Object>> page = readPageEager(20);

            assertEquals(10, page.size());
            assertEquals(
                    1,
                    CommandCounting.finds(CUSTOMERS_COLLECTION),
                    "customers should be read once for the whole page");
        }

        @Test
        @Timeout(300)
        @DisplayName("and every row still gets its own related object")
        void everyRowIsStillFilled() {
            givenStorages(null);

            String asha = write(CUSTOMERS, "name", "Asha");
            String bo = write(CUSTOMERS, "name", "Bo");
            for (int i = 0; i < 10; i++) writeOrder("A" + i, i % 2 == 0 ? asha : bo, null);

            List<Map<String, Object>> page = readPageEager(20);

            List<String> names = page.stream()
                    .map(r -> (Map<?, ?>) r.get("customer"))
                    .map(c -> String.valueOf(c.get("name")))
                    .toList();

            assertEquals(5, names.stream().filter("Asha"::equals).count());
            assertEquals(5, names.stream().filter("Bo"::equals).count());
        }
    }
}
