package com.fincity.saas.core.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.model.DataObject;
import com.fincity.saas.commons.core.service.connection.appdata.AppDataService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.mongodb.reactivestreams.client.MongoClient;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Draft data lands in its own Mongo database.
 *
 * The namespace is `<clientCode>_<appCode>_draft`, and the collection name is
 * unchanged, so a storage keeps the same physical name on both surfaces and a
 * publish never has to rename or move anything. Mongo creates the database and
 * collection lazily, so nothing is provisioned up front.
 */
@DisplayName("Draft data namespace")
class DraftDataNamespaceIntegrationTest extends AbstractIntegrationTest {

    private static final String STORAGE_NAME = "testStorage";
    private static final String UNIQUE_NAME = "testapp_system_teststorage";

    private static final String LIVE_DB = SYSTEM + "_" + APP_CODE;
    private static final String DRAFT_DB = SYSTEM + "_" + APP_CODE + "_draft";

    @Autowired
    private AppDataService appDataService;

    @Autowired
    private MongoClient mongoClient;

    /**
     * App data lives in its own databases, `<client>_<app>` and its `_draft`
     * sibling, which the base class cleanup never sees: that only drops
     * collections in the Spring Data database. Without this, rows from one test
     * are still there for the next one and the isolation assertions below pass or
     * fail on ordering.
     */
    @BeforeEach
    @AfterEach
    void dropAppDataDatabases() {
        Mono.from(this.mongoClient.getDatabase(LIVE_DB).drop()).block();
        Mono.from(this.mongoClient.getDatabase(DRAFT_DB).drop()).block();
        // Index provisioning is memoised per (storage, app, client, surface); a
        // dropped collection with a warm cache would never get its indexes back.
        this.cacheService.evictAllCaches().block();
    }

    private void storedStorage() {

        Storage storage = new Storage();
        storage.setName(STORAGE_NAME)
                .setAppCode(APP_CODE)
                .setClientCode(SYSTEM)
                .setVersion(1);

        Map<String, Object> schema = new HashMap<>();
        schema.put("name", "TestStorage");
        schema.put("type", "OBJECT");
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> titleField = new HashMap<>();
        titleField.put("type", "STRING");
        properties.put("title", titleField);
        schema.put("properties", properties);

        storage.setSchema(schema);
        storage.setUniqueName(UNIQUE_NAME);
        this.insertRaw(storage);
    }

    private <T> T asClient(Mono<T> mono) {
        ContextAuthentication ca = this.authFor(SYSTEM, allAuthoritiesFor("Storage"));
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca)).block();
    }

    private <T> T asDraftClient(Mono<T> mono) {
        ContextAuthentication ca = this.authFor(SYSTEM, allAuthoritiesFor("Storage"));
        return this.onDraftSurface(mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca))).block();
    }

    private DataObject row(String title) {
        Map<String, Object> data = new HashMap<>();
        data.put("title", title);
        return new DataObject().setData(data);
    }

    /**
     * Asserting against the physical databases directly, not through the service.
     * The whole claim being tested is where the bytes land, so going back through
     * the same code path that decides that would prove nothing.
     */
    private List<String> collectionsIn(String database) {
        return Flux.from(this.mongoClient.getDatabase(database).listCollectionNames())
                .collectList()
                .block();
    }

    @Test
    @Timeout(60)
    @DisplayName("a write on the draft surface lands in the draft database, not the live one")
    void draftWriteIsolated() {

        setInheritance(List.of(SYSTEM));
        storedStorage();

        asDraftClient(appDataService.create(APP_CODE, SYSTEM, STORAGE_NAME, row("draft row"), false, null));

        List<String> draftCollections = collectionsIn(DRAFT_DB);
        assertNotNull(draftCollections);
        assertTrue(draftCollections.contains(UNIQUE_NAME),
                "the draft database must hold the collection, found: " + draftCollections);

        List<String> liveCollections = collectionsIn(LIVE_DB);
        assertNotNull(liveCollections);
        assertFalse(liveCollections.contains(UNIQUE_NAME),
                "the live database must be untouched by a draft write, found: " + liveCollections);
    }

    @Test
    @Timeout(60)
    @DisplayName("the two surfaces read their own rows")
    void surfacesReadTheirOwnRows() {

        setInheritance(List.of(SYSTEM));
        storedStorage();

        asClient(appDataService.create(APP_CODE, SYSTEM, STORAGE_NAME, row("live row"), false, null));
        asDraftClient(appDataService.create(APP_CODE, SYSTEM, STORAGE_NAME, row("draft row"), false, null));

        var livePage = asClient(appDataService.readPage(APP_CODE, SYSTEM, STORAGE_NAME,
                new com.fincity.saas.commons.model.Query().setPage(0).setSize(50)));
        var draftPage = asDraftClient(appDataService.readPage(APP_CODE, SYSTEM, STORAGE_NAME,
                new com.fincity.saas.commons.model.Query().setPage(0).setSize(50)));

        assertNotNull(livePage);
        assertNotNull(draftPage);
        assertEquals(1, livePage.getContent().size(), "the live surface must see only its own row");
        assertEquals(1, draftPage.getContent().size(), "the draft surface must see only its own row");
        assertEquals("live row", livePage.getContent().getFirst().get("title"));
        assertEquals("draft row", draftPage.getContent().getFirst().get("title"));
    }

    @Test
    @Timeout(60)
    @DisplayName("metering counts rows on both surfaces")
    void meteringCountsBothSurfaces() {

        setInheritance(List.of(SYSTEM));
        storedStorage();

        asClient(appDataService.create(APP_CODE, SYSTEM, STORAGE_NAME, row("live one"), false, null));
        asClient(appDataService.create(APP_CODE, SYSTEM, STORAGE_NAME, row("live two"), false, null));
        asDraftClient(appDataService.create(APP_CODE, SYSTEM, STORAGE_NAME, row("draft one"), false, null));

        // Deliberately NOT on the draft surface. The meter runs from a scheduled job
        // with no request context, so a test that set the draft context would be
        // proving something the real caller never does.
        Long total = appDataService.estimatedRowCount(APP_CODE, SYSTEM).block();

        assertNotNull(total);
        assertEquals(3L, total, "draft rows must be metered alongside live ones");
    }

    @Test
    @Timeout(60)
    @DisplayName("metering tolerates an app that has never had a draft surface")
    void meteringWithNoDraftDatabase() {

        setInheritance(List.of(SYSTEM));
        storedStorage();

        asClient(appDataService.create(APP_CODE, SYSTEM, STORAGE_NAME, row("live one"), false, null));

        // Most apps never get a draft surface, so a missing draft database is the
        // normal case and must not fail the metering window.
        Long total = appDataService.estimatedRowCount(APP_CODE, SYSTEM).block();

        assertNotNull(total);
        assertEquals(1L, total);
    }

    @Test
    @Timeout(60)
    @DisplayName("the collection name is identical on both surfaces, so a publish moves nothing")
    void sameCollectionNameBothSurfaces() {

        setInheritance(List.of(SYSTEM));
        storedStorage();

        asClient(appDataService.create(APP_CODE, SYSTEM, STORAGE_NAME, row("live row"), false, null));
        asDraftClient(appDataService.create(APP_CODE, SYSTEM, STORAGE_NAME, row("draft row"), false, null));

        assertTrue(collectionsIn(LIVE_DB).contains(UNIQUE_NAME));
        assertTrue(collectionsIn(DRAFT_DB).contains(UNIQUE_NAME));
    }
}
