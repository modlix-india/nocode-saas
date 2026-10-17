package com.fincity.saas.core.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.core.document.Connection;
import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.enums.ConnectionSubType;
import com.fincity.saas.commons.core.enums.ConnectionType;
import com.fincity.saas.commons.core.enums.StorageRelationConstraint;
import com.fincity.saas.commons.core.enums.StorageRelationType;
import com.fincity.saas.commons.core.model.DataObject;
import com.fincity.saas.commons.core.model.StorageRelation;
import com.fincity.saas.commons.core.service.connection.appdata.AppDataService;
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;

import reactor.core.publisher.Mono;

/**
 * App-level storages on MySQL: one table for every client of the app, in the
 * schema of the client hosting it (the URL's client), as on Mongo.
 *
 * Two tenants, TENA and TENB, both use the app on SYSTEM's host. `shared` is app
 * level, `own` is not. Before, MySQL ignored the flag and gave each tenant its own
 * `shared` table, so a client and its customers never saw each other's rows.
 */
@DisplayName("MySQL app-level storages share one table in the host's schema")
class MySQLAppLevelStorageIntegrationTest extends AbstractMySQLSpringIntegrationTest {

    private static final String TENA = "TENA";
    private static final String TENB = "TENB";
    private static final String SHARED = "shared";
    private static final String OWN = "own";

    @Autowired
    private AppDataService appDataService;

    private static DSLContext ctx;

    @BeforeEach
    void setUp() {

        Mockito.when(this.inheritanceService.order(Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenAnswer(inv -> {
                    List<String> out = new ArrayList<>(List.of(SYSTEM));
                    String client = inv.getArgument(2);
                    if (client != null && !out.contains(client))
                        out.add(client);
                    return Mono.just(out);
                });
        Mockito.when(this.feignAuthenticationService.getClientHierarchyCodes(Mockito.anyString()))
                .thenAnswer(inv -> Mono.just(SYSTEM.equals(inv.getArgument(0)) ? List.of(SYSTEM)
                        : List.of(inv.getArgument(0), SYSTEM)));

        ctx = mysql();
        for (String c : List.of(SYSTEM, TENA, TENB)) {
            exec("DROP DATABASE IF EXISTS `" + c + "_" + APP_CODE + "`");
            exec("DROP DATABASE IF EXISTS `" + c + "_" + APP_CODE + "_draft`");
        }

        this.storage(SHARED, "testapp_shared", true, null);
        this.storage(OWN, "testapp_own", false, Map.of("customer", new StorageRelation()
                .setStorageName(SHARED)
                .setRelationType(StorageRelationType.TO_ONE)
                .setFieldName("_id")
                .setDeleteConstraint(StorageRelationConstraint.RESTRICT)));
        this.connection();
    }

    private static void exec(String sql) {
        Mono.from(ctx.query(sql)).block();
    }

    private static boolean tableExists(String schema, String table) {
        Number n = Mono.from(ctx.resultQuery("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = '"
                + schema + "' AND TABLE_NAME = '" + table + "'")).map(r -> (Number) r.get(0)).block();
        return n != null && n.longValue() > 0;
    }

    private static long foreignKeysOn(String schema, String table) {
        Number n = Mono.from(ctx.resultQuery("SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS"
                + " WHERE CONSTRAINT_SCHEMA = '" + schema + "' AND TABLE_NAME = '" + table + "'"))
                .map(r -> (Number) r.get(0)).block();
        return n == null ? 0 : n.longValue();
    }

    private void storage(String name, String table, boolean appLevel, Map<String, StorageRelation> relations) {
        Storage s = new Storage();
        s.setName(name).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        s.setUniqueName(table);
        s.setIsAppLevel(appLevel);
        if (relations != null)
            s.setRelations(new LinkedHashMap<>(relations));
        s.setSchema(new HashMap<>(Map.of("type", "OBJECT", "properties",
                new HashMap<>(Map.of("label", new HashMap<>(Map.of("type", "STRING", "maxLength", 40)))))));
        this.insertRaw(s);
    }

    private void connection() {
        Connection conn = new Connection();
        conn.setConnectionType(ConnectionType.APP_DATA)
                .setConnectionSubType(ConnectionSubType.MYSQL)
                .setIsAppLevel(Boolean.TRUE)
                .setConnectionDetails(new HashMap<>(Map.of("url", mysqlUrl(), "username", "root", "password",
                        "test")));
        conn.setName("appData").setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        conn.setId("0000000000000000appdata1");
        this.insertRaw(conn);
    }

    /** A user of `client` on SYSTEM's host: the URL's client is the host, not the tenant. */
    private <T> T as(String client, Mono<T> mono) {
        ContextAuthentication ca = this.authFor(client, allAuthoritiesFor("Storage"));
        ca.setUrlClientCode(SYSTEM);
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca)).block();
    }

    private Map<String, Object> write(String client, String storage, Map<String, Object> row) {
        return as(client, this.appDataService.create(APP_CODE, null, storage,
                new DataObject().setData(new LinkedHashMap<>(row)), false, null));
    }

    private List<Map<String, Object>> rows(String client, String storage) {
        Page<Map<String, Object>> page = as(client, this.appDataService.readPage(APP_CODE, null, storage,
                new Query().setPage(0).setSize(50)));
        return page.getContent();
    }

    @Test
    @Timeout(120)
    @DisplayName("a row one tenant writes to an app-level storage is read by another, from the host's schema")
    void appLevelRowsAreShared() {

        write(TENA, SHARED, Map.of("label", "from A"));

        List<Map<String, Object>> seenByB = rows(TENB, SHARED);
        assertEquals(1, seenByB.size());
        assertEquals("from A", seenByB.get(0).get("label"));

        assertTrue(tableExists(SYSTEM + "_" + APP_CODE, "testapp_shared"), "held by the host");
        assertFalse(tableExists(TENA + "_" + APP_CODE, "testapp_shared"), "not a table per tenant");
        assertFalse(tableExists(TENB + "_" + APP_CODE, "testapp_shared"));
    }

    @Test
    @Timeout(120)
    @DisplayName("a storage that is not app level stays per tenant")
    void tenantRowsStayPerTenant() {

        write(TENA, OWN, Map.of("label", "A only"));

        assertEquals(1, rows(TENA, OWN).size());
        assertEquals(0, rows(TENB, OWN).size());
        assertTrue(tableExists(TENA + "_" + APP_CODE, "testapp_own"));
        assertFalse(tableExists(SYSTEM + "_" + APP_CODE, "testapp_own"));
    }

    @Test
    @Timeout(120)
    @DisplayName("a relation from a tenant table to an app-level one gets no cross-schema key, and writes still work")
    void noForeignKeyAcrossSchemas() {

        Map<String, Object> customer = write(TENA, SHARED, Map.of("label", "customer"));
        assertNotNull(customer.get("_id"));

        Map<String, Object> order = new LinkedHashMap<>(Map.of("label", "order"));
        order.put("customer", customer.get("_id"));
        write(TENA, OWN, order);

        assertEquals(0, foreignKeysOn(TENA + "_" + APP_CODE, "testapp_own"),
                "the key would point at a table that is not in this schema");
        assertFalse(tableExists(TENA + "_" + APP_CODE, "testapp_shared"),
                "and no stray per-tenant copy of the shared table was created for it");
        assertEquals(1, rows(TENA, OWN).size());
    }
}
