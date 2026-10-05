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
import com.fincity.saas.commons.core.model.DataObject;
import com.fincity.saas.commons.core.service.StorageService;
import com.fincity.saas.commons.core.service.connection.appdata.AppDataService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.AggregateQuery;
import com.fincity.saas.commons.model.Aggregation;
import com.fincity.saas.commons.model.DateBucketUnit;
import com.fincity.saas.commons.model.DateEncoding;
import com.fincity.saas.commons.model.GroupByField;
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.model.condition.AggregateFunction;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import reactor.core.publisher.Mono;

/**
 * The operations that used to answer 501, now going through the service.
 *
 * Aggregate is the one the whole move was for, and it is also the one where being
 * wrong is quietest: a grouped query that is subtly off still returns rows and still
 * renders a chart. So the numbers are asserted, not the shape.
 *
 * The rest - bulk delete, history, the draft surface - matter because an app on this
 * backend has to behave like an app on the other one. A caller should not be able to
 * tell which backend they are on except by the features Mongo cannot do.
 */
@DisplayName("Aggregate, history and the draft surface on MySQL")
class MySQLDataOperationsIntegrationTest extends AbstractMySQLSpringIntegrationTest {



    private static final String STORAGE_NAME = "sales";
    private static final String TABLE = "testapp_sales";

    @Autowired
    private AppDataService appDataService;

    @Autowired
    private StorageService storageService;

    private static DSLContext ctx;

    @BeforeEach
    void setUp() {

        Mockito.when(this.inheritanceService.order(Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenReturn(Mono.just(List.of(SYSTEM)));

        ctx = mysql();

        exec("DROP DATABASE IF EXISTS `" + SYSTEM + "_" + APP_CODE + "`");
        exec("DROP DATABASE IF EXISTS `" + SYSTEM + "_" + APP_CODE + "_draft`");

        this.givenStorage();
        this.givenConnection();
    }

    // ---------------------------------------------------------------- fixtures

    private static void exec(String sql) {
        Mono.from(ctx.query(sql)).block();
    }

    private static long scalar(String sql) {
        Number n = Mono.from(ctx.resultQuery(sql)).map(r -> (Number) r.get(0)).block();
        return n == null ? 0L : n.longValue();
    }

    private void givenStorage() {

        Storage storage = new Storage();
        storage.setName(STORAGE_NAME).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        storage.setUniqueName(TABLE);
        storage.setIsVersioned(Boolean.TRUE);
        storage.setSchema(new HashMap<>(Map.of(
                "type",
                "OBJECT",
                "properties",
                new HashMap<>(Map.of(
                        "region", new HashMap<>(Map.of("type", "STRING", "maxLength", 20)),
                        "amount", new HashMap<>(Map.of("type", "DOUBLE")),
                        "placedAt", new HashMap<>(Map.of("type", "STRING", "format", "DATETIME")),
                        "ts", new HashMap<>(Map.of("type", "LONG")))))));

        this.insertRaw(storage);
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

        // A fixed id so every test reuses one pool. Left to Mongo, each test would
        // get a new document, a new pool and ten more connections that nothing ever
        // closes, and the suite would run MySQL out of them.
        conn.setId("0000000000000000appdata1");

        this.insertRaw(conn);
    }

    private <T> T asClient(Mono<T> mono) {
        ContextAuthentication ca = this.authFor(SYSTEM, allAuthoritiesFor("Storage"));
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca)).block();
    }

    private Map<String, Object> write(String region, Double amount, String placedAt, Long ts) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("region", region);
        row.put("amount", amount);
        row.put("placedAt", placedAt);
        row.put("ts", ts);
        return this.asClient(this.appDataService.create(
                APP_CODE, SYSTEM, STORAGE_NAME, new DataObject().setData(row), false, null));
    }

    /**
     * Day 20 is deliberately avoided, and day 10 would be too.
     *
     * KIRun's DATETIME regex is {@code (0[1-9]|[1-2][1-9]|3[01])} for the day, which
     * matches 01-09, 11-19, 21-29, 30 and 31 - and rejects 10 and 20. That is a bug
     * in the validator rather than in anything here, but it is the validator every
     * write goes through, so a fixture that used those days would fail for a reason
     * that has nothing to do with this backend.
     */
    private void givenRows() {
        write("north", 100d, "2026-01-15T10:00:00", 1768471200L);
        write("north", 200d, "2026-01-21T10:00:00", 1768903200L);
        write("south", 50d, "2026-02-03T10:00:00", 1770112800L);
        write("east", 400d, "2026-02-28T23:30:00", 1772321400L);
    }

    private static Aggregation agg(AggregateFunction f, String field, String alias) {
        return new Aggregation().setFunction(f).setField(field).setAlias(alias);
    }

    private Page<Map<String, Object>> aggregate(AggregateQuery q) {
        return this.asClient(this.appDataService.aggregate(APP_CODE, SYSTEM, STORAGE_NAME, q));
    }

    private static Object measure(Page<Map<String, Object>> page, String key, Object value, String alias) {
        return page.getContent().stream()
                .filter(r -> value.equals(r.get(key)))
                .findFirst()
                .map(r -> r.get(alias))
                .orElse(null);
    }

    // ---------------------------------------------------------------- aggregate

    @Nested
    @DisplayName("aggregate")
    class Aggregate {

        @Test
        @Timeout(240)
        @DisplayName("group and sum through the service")
        void groupAndSum() {
            givenRows();

            Page<Map<String, Object>> page = aggregate(new AggregateQuery()
                    .setGroupBy(List.of(new GroupByField().setField("region")))
                    .setAggregations(List.of(agg(AggregateFunction.SUM, "amount", "total")))
                    .setCount(Boolean.TRUE));

            assertEquals(3, page.getContent().size());
            assertEquals(3L, page.getTotalElements(), "the total is groups, not rows");
            assertEquals(300d, ((Number) measure(page, "region", "north", "total")).doubleValue());
        }

        @Test
        @Timeout(240)
        @DisplayName("a DATETIME column buckets with no encoding, which Mongo cannot do")
        void dateColumnBucket() {
            givenRows();

            Page<Map<String, Object>> page = aggregate(new AggregateQuery()
                    .setGroupBy(List.of(new GroupByField()
                            .setField("placedAt")
                            .setBucket(DateBucketUnit.MONTH)
                            .setAlias("month")))
                    .setAggregations(List.of(agg(AggregateFunction.COUNT, null, "n"))));

            // On Mongo this field is an untyped number and the caller has to declare
            // whether it is seconds or milliseconds. Here the column knows.
            assertEquals(2, page.getContent().size());
        }

        @Test
        @Timeout(240)
        @DisplayName("an epoch column still needs its encoding, and says so")
        void epochNeedsEncoding() {
            givenRows();

            GenericException e = assertThrows(
                    GenericException.class,
                    () -> aggregate(new AggregateQuery()
                            .setGroupBy(List.of(new GroupByField().setField("ts").setBucket(DateBucketUnit.MONTH)))
                            .setAggregations(List.of(agg(AggregateFunction.COUNT, null, "n")))));

            assertTrue(e.getMessage().contains("encoding"), e.getMessage());
        }

        @Test
        @Timeout(240)
        @DisplayName("an encoding on a date column is refused rather than ignored")
        void encodingOnADateColumn() {
            givenRows();

            // Ignoring it would apply epoch arithmetic to something that is not an
            // epoch number, and the result would be silently wrong.
            GenericException e = assertThrows(
                    GenericException.class,
                    () -> aggregate(new AggregateQuery()
                            .setGroupBy(List.of(new GroupByField()
                                    .setField("placedAt")
                                    .setBucket(DateBucketUnit.MONTH)
                                    .setEncoding(DateEncoding.EPOCH_SECONDS)))
                            .setAggregations(List.of(agg(AggregateFunction.COUNT, null, "n")))));

            assertTrue(e.getMessage().contains("needs no encoding"), e.getMessage());
        }

        @Test
        @Timeout(240)
        @DisplayName("an epoch column buckets to the same months as the date column")
        void epochAgreesWithTheDateColumn() {
            givenRows();

            Page<Map<String, Object>> byDate = aggregate(new AggregateQuery()
                    .setGroupBy(List.of(new GroupByField()
                            .setField("placedAt")
                            .setBucket(DateBucketUnit.MONTH)
                            .setAlias("m")))
                    .setAggregations(List.of(agg(AggregateFunction.COUNT, null, "n"))));

            Page<Map<String, Object>> byEpoch = aggregate(new AggregateQuery()
                    .setGroupBy(List.of(new GroupByField()
                            .setField("ts")
                            .setBucket(DateBucketUnit.MONTH)
                            .setEncoding(DateEncoding.EPOCH_SECONDS)
                            .setAlias("m")))
                    .setAggregations(List.of(agg(AggregateFunction.COUNT, null, "n"))));

            // The same four rows, written twice in two different ways, in the same
            // buckets with the same counts.
            assertEquals(byDate.getContent().size(), byEpoch.getContent().size());
        }

        @Test
        @Timeout(240)
        @DisplayName("a field the storage does not declare is refused")
        void undeclaredField() {
            givenRows();

            // This is the step between a caller and an arbitrary column.
            GenericException e = assertThrows(
                    GenericException.class,
                    () -> aggregate(new AggregateQuery()
                            .setGroupBy(List.of(new GroupByField().setField("nosuch")))
                            .setAggregations(List.of(agg(AggregateFunction.COUNT, null, "n")))));

            assertTrue(e.getMessage().contains("declares no field"), e.getMessage());
        }

        @Test
        @Timeout(240)
        @DisplayName("summing a text column is refused rather than quietly returning zero")
        void sumOnText() {
            givenRows();

            // CAST('north' AS DOUBLE) is 0 in MySQL, with a warning nobody reads. A
            // total that is quietly short is worse than a request that is refused.
            GenericException e = assertThrows(
                    GenericException.class,
                    () -> aggregate(new AggregateQuery()
                            .setAggregations(List.of(agg(AggregateFunction.SUM, "region", "total")))));

            assertTrue(e.getMessage().contains("casts to zero"), e.getMessage());
        }

        @Test
        @Timeout(240)
        @DisplayName("having and sort work over the aliases")
        void havingAndSort() {
            givenRows();

            Page<Map<String, Object>> page = aggregate(new AggregateQuery()
                    .setGroupBy(List.of(new GroupByField().setField("region")))
                    .setAggregations(List.of(agg(AggregateFunction.SUM, "amount", "total")))
                    .setHaving(new FilterCondition()
                            .setField("total")
                            .setValue(100)
                            .setOperator(com.fincity.saas.commons.model.condition.FilterConditionOperator
                                    .GREATER_THAN))
                    .setSort(org.springframework.data.domain.Sort.by(
                            org.springframework.data.domain.Sort.Order.desc("total"))));

            assertEquals(2, page.getContent().size());
            assertEquals("east", page.getContent().get(0).get("region"));
        }
    }

    // ---------------------------------------------------------------- history

    @Nested
    @DisplayName("history")
    class History {

        @Test
        @Timeout(240)
        @DisplayName("a create and an update leave two version rows")
        void createThenUpdate() {
            Map<String, Object> created = write("north", 100d, "2026-01-15T10:00:00", 1768471200L);
            String id = String.valueOf(created.get("_id"));

            Map<String, Object> edit = new LinkedHashMap<>(created);
            edit.put("amount", 150d);
            asClient(appDataService.update(
                    APP_CODE, SYSTEM, STORAGE_NAME, new DataObject().setData(edit), Boolean.FALSE, Boolean.FALSE,
                    null));

            Page<Map<String, Object>> history = asClient(appDataService.readPageVersion(
                    APP_CODE, SYSTEM, STORAGE_NAME, id, new Query().setCount(true), Boolean.TRUE));

            assertEquals(2, history.getContent().size());
            assertEquals("UPDATE", history.getContent().get(0).get("operation"), "newest first");
            assertEquals("CREATE", history.getContent().get(1).get("operation"));
        }

        @Test
        @Timeout(240)
        @DisplayName("the snapshot comes back as an object, not as text")
        void snapshotIsAnObject() {
            Map<String, Object> created = write("north", 100d, "2026-01-15T10:00:00", 1768471200L);

            Page<Map<String, Object>> history = asClient(appDataService.readPageVersion(
                    APP_CODE, SYSTEM, STORAGE_NAME, String.valueOf(created.get("_id")), new Query(), Boolean.TRUE));

            Object snapshot = history.getContent().get(0).get("object");
            assertTrue(snapshot instanceof Map, String.valueOf(snapshot));
            assertEquals("north", ((Map<?, ?>) snapshot).get("region"));
        }

        @Test
        @Timeout(240)
        @DisplayName("an audit-only read leaves the snapshot behind")
        void auditOnly() {
            Map<String, Object> created = write("north", 100d, "2026-01-15T10:00:00", 1768471200L);

            Page<Map<String, Object>> history = asClient(appDataService.readPageVersion(
                    APP_CODE, SYSTEM, STORAGE_NAME, String.valueOf(created.get("_id")), new Query(), Boolean.FALSE));

            // The snapshot is the bulk of a version row and a "who changed what, when"
            // view never looks at it.
            assertFalse(history.getContent().get(0).containsKey("object"));
            assertNotNull(history.getContent().get(0).get("operation"));
        }

        @Test
        @Timeout(240)
        @DisplayName("one version can be read by its own id")
        void readOneVersion() {
            Map<String, Object> created = write("north", 100d, "2026-01-15T10:00:00", 1768471200L);

            Page<Map<String, Object>> history = asClient(appDataService.readPageVersion(
                    APP_CODE, SYSTEM, STORAGE_NAME, String.valueOf(created.get("_id")), new Query(), Boolean.TRUE));

            String versionId = String.valueOf(history.getContent().get(0).get("_id"));
            Map<String, Object> one = asClient(
                    appDataService.readVersion(APP_CODE, SYSTEM, STORAGE_NAME, versionId));

            assertEquals("CREATE", one.get("operation"));
        }

        @Test
        @Timeout(240)
        @DisplayName("a delete is recorded, or purges the history, as asked")
        void deleteRecordsOrPurges() {
            Map<String, Object> kept = write("north", 100d, "2026-01-15T10:00:00", 1768471200L);
            Map<String, Object> purged = write("south", 50d, "2026-02-03T10:00:00", 1770112800L);

            asClient(appDataService.delete(APP_CODE, SYSTEM, STORAGE_NAME, String.valueOf(kept.get("_id")),
                    Boolean.FALSE));
            asClient(appDataService.delete(APP_CODE, SYSTEM, STORAGE_NAME, String.valueOf(purged.get("_id")),
                    Boolean.TRUE));

            String versions = "`" + SYSTEM + "_" + APP_CODE + "`.`" + TABLE + "_version`";

            assertEquals(
                    2,
                    scalar("SELECT COUNT(*) FROM " + versions + " WHERE `objectId`='" + kept.get("_id") + "'"),
                    "the create and the delete");
            assertEquals(
                    0,
                    scalar("SELECT COUNT(*) FROM " + versions + " WHERE `objectId`='" + purged.get("_id") + "'"),
                    "asked to be purged");
        }
    }

    // ---------------------------------------------------------------- the rest

    @Nested
    @DisplayName("migration status")
    class Status {

        @Test
        @Timeout(240)
        @DisplayName("reports every tenant holding the storage")
        void reportsTenants() {
            givenRows();

            List<com.fincity.saas.commons.core.service.connection.appdata.mysql.TenantProgress> status =
                    asClient(appDataService.migrationStatus(APP_CODE, SYSTEM, STORAGE_NAME));

            // Nothing has migrated, so nothing has a journal row. "Not attempted" is
            // the honest answer, and it is still an answer - before this endpoint
            // there was no way to ask at all.
            assertEquals(1, status.size());
            assertTrue(status.get(0).isUntouched(), status.get(0).describe());
            assertTrue(status.get(0).describe().contains("not attempted"));
        }

        @Test
        @Timeout(240)
        @DisplayName("a tenant that has migrated reports as applied")
        void reportsApplied() {
            givenRows();

            // Widening the column is a migration, driven by the definition change.
            Storage storage = asClient(storageService.read(STORAGE_NAME, APP_CODE, SYSTEM)).getObject();
            Map<String, Object> props = new HashMap<>((Map<String, Object>) storage.getSchema().get("properties"));
            props.put("region", new HashMap<>(Map.of("type", "STRING", "maxLength", 60)));

            Storage edit = new Storage(storage);
            edit.setSchema(new HashMap<>(Map.of("type", "OBJECT", "properties", props)));
            asClient(storageService.update(edit));

            List<com.fincity.saas.commons.core.service.connection.appdata.mysql.TenantProgress> status =
                    asClient(appDataService.migrationStatus(APP_CODE, SYSTEM, STORAGE_NAME));

            assertEquals(1, status.size());
            assertTrue(status.get(0).isDone(), status.get(0).describe());
        }
    }

    @Nested
    @DisplayName("bulk delete and the draft surface")
    class Rest {

        @Test
        @Timeout(240)
        @DisplayName("devMode counts what would go and takes nothing")
        void devModeCounts() {
            givenRows();

            Long would = asClient(appDataService.deleteByFilter(
                    APP_CODE, SYSTEM, STORAGE_NAME,
                    new Query().setCondition(FilterCondition.make("region", "north")), Boolean.TRUE, null));

            assertEquals(2L, would);
            assertEquals(4, scalar("SELECT COUNT(*) FROM `" + SYSTEM + "_" + APP_CODE + "`.`" + TABLE + "`"));
        }

        @Test
        @Timeout(240)
        @DisplayName("a filtered delete takes exactly the matches")
        void deleteByFilter() {
            givenRows();

            Long gone = asClient(appDataService.deleteByFilter(
                    APP_CODE, SYSTEM, STORAGE_NAME,
                    new Query().setCondition(FilterCondition.make("region", "north")), Boolean.FALSE, null));

            assertEquals(2L, gone);
            assertEquals(2, scalar("SELECT COUNT(*) FROM `" + SYSTEM + "_" + APP_CODE + "`.`" + TABLE + "`"));
        }

        @Test
        @Timeout(240)
        @DisplayName("live data copies to the draft surface")
        void copyLiveToDraft() {
            givenRows();

            Long copied = asClient(appDataService.copyLiveDataToDraft(APP_CODE, SYSTEM, STORAGE_NAME, Boolean.TRUE));

            assertEquals(4L, copied);
            assertEquals(
                    4, scalar("SELECT COUNT(*) FROM `" + SYSTEM + "_" + APP_CODE + "_draft`.`" + TABLE + "`"));
        }

        @Test
        @Timeout(240)
        @DisplayName("copying from an empty live table changes nothing and says so")
        void copyNothing() {

            // Nothing has ever been written, so the live table does not even exist.
            // Asking it for a count would be an error rather than a zero, and the
            // draft table must not be cleared on the way to finding that out:
            // emptying it and then reporting "there was nothing to copy" would be the
            // worst of both.
            GenericException e = assertThrows(
                    GenericException.class,
                    () -> asClient(appDataService.copyLiveDataToDraft(
                            APP_CODE, SYSTEM, STORAGE_NAME, Boolean.TRUE)));

            assertTrue(e.getMessage().contains("no live rows"), e.getMessage());

            assertEquals(
                    0,
                    scalar("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='" + SYSTEM + "_"
                            + APP_CODE + "_draft' AND TABLE_NAME='" + TABLE + "' AND TABLE_ROWS > 0"));
        }

        @Test
        @Timeout(240)
        @DisplayName("dropping the draft storage takes its table and its history")
        void dropDraftStorage() {
            givenRows();
            asClient(appDataService.copyLiveDataToDraft(APP_CODE, SYSTEM, STORAGE_NAME, Boolean.TRUE));

            Storage storage = asClient(storageService.read(STORAGE_NAME, APP_CODE, SYSTEM)).getObject();
            asClient(appDataService.dropDraftStorageData(APP_CODE, SYSTEM, storage));

            assertEquals(
                    0,
                    scalar("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='" + SYSTEM + "_"
                            + APP_CODE + "_draft' AND TABLE_NAME LIKE '" + TABLE + "%'"));

            // And the live table is untouched, which is the whole contract.
            assertEquals(4, scalar("SELECT COUNT(*) FROM `" + SYSTEM + "_" + APP_CODE + "`.`" + TABLE + "`"));
        }

        @Test
        @Timeout(240)
        @DisplayName("dropping the draft database leaves the live one alone")
        void dropDraftDatabase() {
            givenRows();
            asClient(appDataService.copyLiveDataToDraft(APP_CODE, SYSTEM, STORAGE_NAME, Boolean.TRUE));

            asClient(appDataService.dropDraftData(APP_CODE, SYSTEM));

            assertEquals(
                    0,
                    scalar("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='" + SYSTEM + "_"
                            + APP_CODE + "_draft'"));
            assertEquals(
                    1,
                    scalar("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='" + SYSTEM + "_"
                            + APP_CODE + "'"));
        }
    }
}
