package com.fincity.saas.core.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.enums.StorageRelationType;
import com.fincity.saas.commons.core.model.DataObject;
import com.fincity.saas.commons.core.model.StorageRelation;
import com.fincity.saas.commons.core.service.connection.appdata.AppDataService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.mongodb.reactivestreams.client.MongoClient;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reaching the rows of an {@code onlyThruKIRun} storage.
 *
 * The flag means "route this app's traffic through a KIRun function". It was never
 * meant to hide the rows from whoever is BUILDING the app, and it did: the workspace
 * data browser could not show them on either surface. Worse, it did not refuse
 * cleanly. Every public entry point in {@code AppDataService} chains through
 * {@code FlatMapUtil.flatMapMonoWithNull}, which turns an empty into a null and keeps
 * going, so the denial arrived as a null {@code storage} rather than a short circuit:
 * {@code readPage} answered a misleading "Storage not found" 404 for a storage that
 * plainly exists, and {@code copyToDraft} handed the null to
 * {@code MongoAppDataService.getCollection} and NPE'd on {@code storage.getAppCode()},
 * surfacing as a 500 with a 31KB stack trace.
 *
 * So there are two claims under test here, and they are separable:
 *
 *   1. a builder SESSION may read and write these rows, on both surfaces
 *   2. everyone else is REFUSED, with a message that names the reason, and never
 *      with an NPE or a 404 about a storage that exists
 *
 * The discriminator is {@code verifiedAppCode}, stamped at login and carried in the
 * signed token. NOT {@code urlAppCode}: that one is set by {@code JWTTokenFilter} from
 * the {@code appCode} REQUEST HEADER, and the builder deliberately sends the code of
 * the app being EDITED. Every fixture below leans on that distinction: {@code authFor}
 * sets {@code urlAppCode} to {@code testapp} and never touches
 * {@code verifiedAppCode}, so a plain test session is a NON-builder session no matter
 * what app it names.
 */
@DisplayName("App data on an onlyThruKIRun storage")
class AppDataKIRunOnlyIntegrationTest extends AbstractIntegrationTest {

    private static final String STORAGE_NAME = "kirunOnlyStorage";
    private static final String UNIQUE_NAME = "testapp_system_kirunonlystorage";

    private static final String PARENT_STORAGE_NAME = "kirunOnlyParent";
    private static final String PARENT_UNIQUE_NAME = "testapp_system_kirunonlyparent";
    private static final String RELATION_FIELD = "child";

    private static final String LIVE_DB = SYSTEM + "_" + APP_CODE;
    private static final String DRAFT_DB = SYSTEM + "_" + APP_CODE + "_draft";

    /** The two defaults of {@code core.storage.onlyThruKIRun.builderAppCodes}. */
    private static final String BUILDER_APP = "appbuilder";
    private static final String OTHER_BUILDER_APP = "sitezump";

    @Autowired
    private AppDataService appDataService;

    @Autowired
    private MongoClient mongoClient;

    /**
     * App data lives in {@code <client>_<app>} and its {@code _draft} sibling, neither
     * of which the base class cleanup can see. Index provisioning is memoised per
     * (storage, app, client, surface), so a dropped collection with a warm cache would
     * never get its indexes back.
     */
    @BeforeEach
    @AfterEach
    void dropAppDataDatabases() {
        Mono.from(this.mongoClient.getDatabase(LIVE_DB).drop()).block();
        Mono.from(this.mongoClient.getDatabase(DRAFT_DB).drop()).block();
        this.cacheService.evictAllCaches().block();
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private void storedStorage(boolean onlyThruKIRun) {
        Storage storage = new Storage();
        storage.setName(STORAGE_NAME)
                .setAppCode(APP_CODE)
                .setClientCode(SYSTEM)
                .setVersion(1);
        storage.setSchema(objectSchema("KIRunOnlyStorage"));
        storage.setOnlyThruKIRun(onlyThruKIRun);
        storage.setUniqueName(UNIQUE_NAME);
        this.insertRaw(storage);
    }

    /**
     * A plain storage carrying a TO_ONE relation at {@link #RELATION_FIELD} pointing at
     * the KIRun-only one. Eager-loading that field is the path
     * {@code prepareMonosForPage} takes, and the one that must stay lenient.
     */
    private void storedParentStorage() {
        StorageRelation relation = new StorageRelation();
        relation.setStorageName(STORAGE_NAME)
                .setRelationType(StorageRelationType.TO_ONE)
                .setFieldName(RELATION_FIELD);

        Map<String, StorageRelation> relations = new HashMap<>();
        relations.put(RELATION_FIELD, relation);

        Storage parent = new Storage();
        parent.setName(PARENT_STORAGE_NAME)
                .setAppCode(APP_CODE)
                .setClientCode(SYSTEM)
                .setVersion(1);
        parent.setSchema(objectSchema("KIRunOnlyParent"));
        parent.setRelations(relations);
        parent.setUniqueName(PARENT_UNIQUE_NAME);
        this.insertRaw(parent);
    }

    private Map<String, Object> objectSchema(String name) {
        Map<String, Object> titleField = new HashMap<>();
        titleField.put("type", "STRING");
        Map<String, Object> childField = new HashMap<>();
        childField.put("type", "STRING");

        Map<String, Object> properties = new HashMap<>();
        properties.put("title", titleField);
        properties.put(RELATION_FIELD, childField);

        Map<String, Object> schema = new HashMap<>();
        schema.put("name", name);
        schema.put("type", "OBJECT");
        schema.put("properties", properties);
        return schema;
    }

    /** A NON-builder session: verifiedAppCode is never set, exactly as authFor leaves it. */
    private <T> T asAppUser(Mono<T> mono) {
        ContextAuthentication ca = this.authFor(SYSTEM, allAuthoritiesFor("Storage"));
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca)).block();
    }

    private <T> T asBuilder(Mono<T> mono) {
        return asBuilder(mono, BUILDER_APP);
    }

    private <T> T asBuilder(Mono<T> mono, String verifiedAppCode) {
        ContextAuthentication ca = this.authFor(SYSTEM, allAuthoritiesFor("Storage"))
                .setVerifiedAppCode(verifiedAppCode);
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca)).block();
    }

    private <T> T asBuilderOnDraft(Mono<T> mono) {
        ContextAuthentication ca = this.authFor(SYSTEM, allAuthoritiesFor("Storage"))
                .setVerifiedAppCode(BUILDER_APP);
        return this.onDraftSurface(mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca))).block();
    }

    private DataObject row(String title) {
        Map<String, Object> data = new HashMap<>();
        data.put("title", title);
        return new DataObject().setData(data);
    }

    /** A row written straight to Mongo, so the fixture never depends on what is under test. */
    private String rawRow(String database, String collection, String title) {
        Document doc = new Document("title", title);
        Mono.from(this.mongoClient.getDatabase(database).getCollection(collection).insertOne(doc)).block();
        return doc.getObjectId("_id").toHexString();
    }

    private List<Document> documentsIn(String database, String collection) {
        return Flux.from(this.mongoClient.getDatabase(database).getCollection(collection).find())
                .collectList()
                .block();
    }

    private Query all() {
        return new Query().setPage(0).setSize(50);
    }

    // ── the builder is let in ────────────────────────────────────────────────

    @Test
    @Timeout(60)
    @DisplayName("a builder session reads live rows, where a non-builder saw 'Storage not found'")
    void builderSessionReadsLiveRows() {

        setInheritance(List.of(SYSTEM));
        storedStorage(true);
        rawRow(LIVE_DB, UNIQUE_NAME, "live row");

        var page = asBuilder(appDataService.readPage(APP_CODE, SYSTEM, STORAGE_NAME, all()));

        assertNotNull(page);
        assertEquals(1, page.getTotalElements());
        assertEquals("live row", page.getContent().getFirst().get("title"));
    }

    @Test
    @Timeout(60)
    @DisplayName("a builder session writes live rows")
    void builderSessionWritesLiveRows() {

        setInheritance(List.of(SYSTEM));
        storedStorage(true);

        var created = asBuilder(appDataService.create(APP_CODE, SYSTEM, STORAGE_NAME, row("written"), false, null));

        assertNotNull(created);
        assertEquals("written", created.get("title"));
        assertEquals(1, documentsIn(LIVE_DB, UNIQUE_NAME).size());
    }

    @Test
    @Timeout(60)
    @DisplayName("a builder session reads the draft rows, and does not see the live ones")
    void builderSessionReadsDraftRows() {

        setInheritance(List.of(SYSTEM));
        storedStorage(true);
        rawRow(LIVE_DB, UNIQUE_NAME, "live row");
        rawRow(DRAFT_DB, UNIQUE_NAME, "draft row");

        var page = asBuilderOnDraft(appDataService.readPage(APP_CODE, SYSTEM, STORAGE_NAME, all()));

        assertNotNull(page);
        assertEquals(1, page.getTotalElements());
        assertEquals("draft row", page.getContent().getFirst().get("title"));
    }

    /**
     * The regression from the field report. Before the fix this threw a raw
     * NullPointerException out of {@code MongoAppDataService.getCollection}
     * ("Cannot invoke Storage.getAppCode() because parameter2 is null"), because
     * {@code copyLiveDataToDraft} hands the resolved storage straight to the data
     * service with no null check in between.
     */
    @Test
    @Timeout(60)
    @DisplayName("copyToDraft from a builder session copies the rows instead of NPE-ing")
    void copyToDraftFromABuilderSessionCopiesRows() {

        setInheritance(List.of(SYSTEM));
        storedStorage(true);
        rawRow(LIVE_DB, UNIQUE_NAME, "one");
        rawRow(LIVE_DB, UNIQUE_NAME, "two");

        Long copied = asBuilder(appDataService.copyLiveDataToDraft(APP_CODE, SYSTEM, STORAGE_NAME, true));

        assertEquals(2L, copied);
        assertEquals(2, documentsIn(DRAFT_DB, UNIQUE_NAME).size());
    }

    @Test
    @Timeout(60)
    @DisplayName("every app code in the configured list is a builder, not just appbuilder")
    void configuredBuilderAppCodesAreAllHonoured() {

        setInheritance(List.of(SYSTEM));
        storedStorage(true);
        rawRow(LIVE_DB, UNIQUE_NAME, "live row");

        var page = asBuilder(appDataService.readPage(APP_CODE, SYSTEM, STORAGE_NAME, all()), OTHER_BUILDER_APP);

        assertNotNull(page);
        assertEquals(1, page.getTotalElements());
    }

    // ── everyone else is refused, and told why ──────────────────────────────

    @Test
    @Timeout(60)
    @DisplayName("a non-builder session is refused with a message that names the flag")
    void nonBuilderSessionIsRefusedWithAReason() {

        setInheritance(List.of(SYSTEM));
        storedStorage(true);
        rawRow(LIVE_DB, UNIQUE_NAME, "live row");

        GenericException ex = assertThrows(GenericException.class,
                () -> asAppUser(appDataService.readPage(APP_CODE, SYSTEM, STORAGE_NAME, all())));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("onlyThruKIRun"),
                "the denial must name the flag, not report a missing storage: " + ex.getMessage());
        assertFalse(ex.getMessage().contains("not found"),
                "a storage that exists must never be reported as missing: " + ex.getMessage());
    }

    /**
     * Same route, same NPE before the fix, but from the caller who must still be
     * refused. Asserting the STATUS as well as the message: the old behaviour was a
     * 500, and a 403 is the whole point.
     */
    @Test
    @Timeout(60)
    @DisplayName("copyToDraft from a non-builder session is a 403, not a 500")
    void copyToDraftFromANonBuilderSessionIsRefused() {

        setInheritance(List.of(SYSTEM));
        storedStorage(true);
        rawRow(LIVE_DB, UNIQUE_NAME, "one");

        GenericException ex = assertThrows(GenericException.class,
                () -> asAppUser(appDataService.copyLiveDataToDraft(APP_CODE, SYSTEM, STORAGE_NAME, true)));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("onlyThruKIRun"), ex.getMessage());
        assertTrue(documentsIn(DRAFT_DB, UNIQUE_NAME).isEmpty(), "a refused copy must write nothing");
    }

    @Test
    @Timeout(60)
    @DisplayName("a non-builder session is refused on write too")
    void nonBuilderSessionIsRefusedOnWrite() {

        setInheritance(List.of(SYSTEM));
        storedStorage(true);

        GenericException ex = assertThrows(GenericException.class,
                () -> asAppUser(appDataService.create(APP_CODE, SYSTEM, STORAGE_NAME, row("nope"), false, null)));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertTrue(documentsIn(LIVE_DB, UNIQUE_NAME).isEmpty(), "a refused write must store nothing");
    }

    /**
     * An unknown app code is refused rather than waved through. Guards against the
     * check ever being loosened to "any non-empty verifiedAppCode".
     */
    @Test
    @Timeout(60)
    @DisplayName("an app code outside the configured list is not a builder")
    void anUnconfiguredAppCodeIsRefused() {

        setInheritance(List.of(SYSTEM));
        storedStorage(true);
        rawRow(LIVE_DB, UNIQUE_NAME, "live row");

        GenericException ex = assertThrows(GenericException.class,
                () -> asBuilder(appDataService.readPage(APP_CODE, SYSTEM, STORAGE_NAME, all()), "leadzump"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    // ── the flag still means nothing to a storage that does not carry it ────

    @Test
    @Timeout(60)
    @DisplayName("a storage without the flag is untouched by any of this")
    void aStorageWithoutTheFlagIsUnaffected() {

        setInheritance(List.of(SYSTEM));
        storedStorage(false);
        rawRow(LIVE_DB, UNIQUE_NAME, "live row");

        var asUser = asAppUser(appDataService.readPage(APP_CODE, SYSTEM, STORAGE_NAME, all()));
        var asBuilder = asBuilder(appDataService.readPage(APP_CODE, SYSTEM, STORAGE_NAME, all()));

        assertNotNull(asUser);
        assertNotNull(asBuilder);
        assertEquals(1, asUser.getTotalElements());
        assertEquals(1, asBuilder.getTotalElements());
    }

    // ── the lenient path: relations must not fail the parent read ───────────

    /**
     * {@code prepareMonosForPage} resolves each eager field with plain
     * {@code flatMapMono}, so an empty there short-circuits that ONE relation and the
     * parent row still comes back without it. Making the shared resolver throw would
     * have turned every page eager-loading a relation to a KIRun-only storage into a
     * 403 on the whole read, which is why {@code getStorageForRelation} exists.
     */
    @Test
    @Timeout(60)
    @DisplayName("an eager relation to a KIRun-only storage is skipped, not fatal to the parent")
    void eagerRelationToAKIRunOnlyStorageIsSkipped() {

        setInheritance(List.of(SYSTEM));
        storedStorage(true);
        storedParentStorage();

        String childId = rawRow(LIVE_DB, UNIQUE_NAME, "child row");

        Document parent = new Document("title", "parent row").append(RELATION_FIELD, childId);
        Mono.from(this.mongoClient.getDatabase(LIVE_DB).getCollection(PARENT_UNIQUE_NAME).insertOne(parent)).block();
        String parentId = parent.getObjectId("_id").toHexString();

        var read = asAppUser(appDataService.read(
                APP_CODE, SYSTEM, PARENT_STORAGE_NAME, parentId, true, List.of(RELATION_FIELD)));

        assertNotNull(read, "the parent read must succeed even though the relation is unreachable");
        assertEquals("parent row", read.get("title"));
    }
}
