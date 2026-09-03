package com.fincity.saas.core.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.model.DataObject;
import com.fincity.saas.commons.core.service.connection.appdata.AppDataService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.mongodb.reactivestreams.client.MongoClient;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Naming the data surface, clearing rows without dropping the storage, and
 * seeding draft rows from live.
 *
 * These three go together because they are the builder's side of the draft data
 * surface. The surface is otherwise ambient -- decided by the hostname the
 * gateway resolved -- and the builder runs on the live host, so without an
 * explicit opt-in it can neither see nor manage an app's sandbox rows at all.
 *
 * The assertions go against Mongo directly wherever the claim is about *where*
 * something landed. Reading it back through the same accessor that decided would
 * prove nothing.
 */
@DisplayName("App data surface, clear and seed")
class AppDataSurfaceIntegrationTest extends AbstractIntegrationTest {

    private static final String STORAGE_NAME = "surfaceStorage";
    private static final String UNIQUE_NAME = "testapp_system_surfacestorage";
    private static final String VERSION_COLLECTION = UNIQUE_NAME + "_version";

    private static final String LIVE_DB = SYSTEM + "_" + APP_CODE;
    private static final String DRAFT_DB = SYSTEM + "_" + APP_CODE + "_draft";

    @Autowired
    private AppDataService appDataService;

    @Autowired
    private MongoClient mongoClient;

    /**
     * App data lives in `<client>_<app>` and its `_draft` sibling, neither of
     * which the base class cleanup can see -- that only drops collections in the
     * Spring Data database. Without this, rows from one test survive into the next
     * and every isolation assertion here passes or fails on ordering.
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

    private void storedStorage(boolean versioned) {
        storedStorage(versioned, null);
    }

    /**
     * @param runtimeAuth an authority expression in the APP's own role namespace, the
     *                    shape a real storage uses ({@code rim}'s Project storage says
     *                    {@code Authorities.CXAPP.ROLE_Super_Admin}). No builder holds
     *                    one, which is the whole point of the tests that pass it.
     */
    private void storedStorage(boolean versioned, String runtimeAuth) {

        Storage storage = new Storage();
        storage.setName(STORAGE_NAME)
                .setAppCode(APP_CODE)
                .setClientCode(SYSTEM)
                .setVersion(1);

        if (runtimeAuth != null)
            storage.setCreateAuth(runtimeAuth)
                    .setDeleteAuth(runtimeAuth)
                    .setUpdateAuth(runtimeAuth);

        Map<String, Object> titleField = new HashMap<>();
        titleField.put("type", "STRING");
        Map<String, Object> properties = new HashMap<>();
        properties.put("title", titleField);

        Map<String, Object> schema = new HashMap<>();
        schema.put("name", "SurfaceStorage");
        schema.put("type", "OBJECT");
        schema.put("properties", properties);

        storage.setSchema(schema);
        storage.setIsVersioned(versioned);
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
     * A live row written straight to Mongo, bypassing the service.
     *
     * The fixtures below deliberately set authorities no caller here holds, so going
     * through create() would fail on the way IN and never reach what is under test.
     */
    private void liveRow(String title) {
        Mono.from(this.mongoClient.getDatabase(LIVE_DB).getCollection(UNIQUE_NAME)
                .insertOne(new Document("title", title))).block();
    }

    private List<Document> documentsIn(String database, String collection) {
        return Flux.from(this.mongoClient.getDatabase(database).getCollection(collection).find())
                .collectList()
                .block();
    }

    private List<String> collectionsIn(String database) {
        return Flux.from(this.mongoClient.getDatabase(database).listCollectionNames())
                .collectList()
                .block();
    }

    private Query all() {
        return new Query().setPage(0).setSize(50);
    }

    // ── naming the surface ───────────────────────────────────────────────────

    @Test
    @Timeout(60)
    @DisplayName("draft = true reads the draft rows from a caller on the live surface")
    void explicitDraftReadsDraftRows() {

        setInheritance(List.of(SYSTEM));
        storedStorage(false);

        asClient(appDataService.create(APP_CODE, SYSTEM, STORAGE_NAME, row("live row"), false, null));
        asDraftClient(appDataService.create(APP_CODE, SYSTEM, STORAGE_NAME, row("draft row"), false, null));

        // No onDraftSurface here: this is the builder's position exactly -- ambient
        // flag false, surface named on the call.
        var drafted = asClient(appDataService.onSurface(APP_CODE, Boolean.TRUE,
                appDataService.readPage(APP_CODE, SYSTEM, STORAGE_NAME, all())));

        assertNotNull(drafted);
        assertEquals(1, drafted.getContent().size());
        assertEquals("draft row", drafted.getContent().getFirst().get("title"));
    }

    @Test
    @Timeout(60)
    @DisplayName("draft = false forces live even when the ambient flag says draft")
    void explicitLiveOverridesAmbientDraft() {

        setInheritance(List.of(SYSTEM));
        storedStorage(false);

        asClient(appDataService.create(APP_CODE, SYSTEM, STORAGE_NAME, row("live row"), false, null));
        asDraftClient(appDataService.create(APP_CODE, SYSTEM, STORAGE_NAME, row("draft row"), false, null));

        // The mirror-image case, and the reason both values are gated rather than
        // only TRUE: a caller on the draft surface asking for live.
        var live = asDraftClient(appDataService.onSurface(APP_CODE, Boolean.FALSE,
                appDataService.readPage(APP_CODE, SYSTEM, STORAGE_NAME, all())));

        assertNotNull(live);
        assertEquals(1, live.getContent().size());
        assertEquals("live row", live.getContent().getFirst().get("title"));
    }

    @Test
    @Timeout(60)
    @DisplayName("no draft parameter leaves the ambient surface alone")
    void absentParameterKeepsAmbientSurface() {

        setInheritance(List.of(SYSTEM));
        storedStorage(false);

        asClient(appDataService.create(APP_CODE, SYSTEM, STORAGE_NAME, row("live row"), false, null));
        asDraftClient(appDataService.create(APP_CODE, SYSTEM, STORAGE_NAME, row("draft row"), false, null));

        var ambient = asDraftClient(appDataService.onSurface(APP_CODE, null,
                appDataService.readPage(APP_CODE, SYSTEM, STORAGE_NAME, all())));

        assertNotNull(ambient);
        assertEquals("draft row", ambient.getContent().getFirst().get("title"),
                "a null draft parameter must not disturb the hostname's answer");
    }

    @Test
    @Timeout(60)
    @DisplayName("naming the surface without write access on the app is forbidden")
    void namingSurfaceNeedsWriteAccess() {

        setInheritance(List.of(SYSTEM));
        storedStorage(false);

        // Read authority on the storage is untouched. What is withdrawn is write
        // access to the APP, which is the bar that keeps the draft hostname a
        // credential rather than a formality.
        Mockito.when(this.feignSecurityService.hasWriteAccess(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Mono.just(Boolean.FALSE));

        GenericException ex = assertThrows(GenericException.class, () -> asClient(appDataService.onSurface(
                APP_CODE, Boolean.TRUE, appDataService.readPage(APP_CODE, SYSTEM, STORAGE_NAME, all()))));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    @Timeout(60)
    @DisplayName("a never-written draft collection reads as empty, not as a denial")
    void unwrittenDraftReadsEmpty() {

        setInheritance(List.of(SYSTEM));
        storedStorage(false);

        asClient(appDataService.create(APP_CODE, SYSTEM, STORAGE_NAME, row("live row"), false, null));

        // Most apps have no draft database at all, so this is the normal first
        // answer the Data view gets after switching to the sandbox. It has to be
        // distinguishable from "you may not look".
        var drafted = asClient(appDataService.onSurface(APP_CODE, Boolean.TRUE,
                appDataService.readPage(APP_CODE, SYSTEM, STORAGE_NAME, all())));

        assertNotNull(drafted);
        assertTrue(drafted.getContent().isEmpty());
    }

    // ── clearing rows ────────────────────────────────────────────────────────

    @Test
    @Timeout(60)
    @DisplayName("clearing rows empties the collection and keeps it, with its history")
    void clearKeepsCollectionAndHistory() {

        setInheritance(List.of(SYSTEM));
        storedStorage(true);

        asClient(appDataService.create(APP_CODE, SYSTEM, STORAGE_NAME, row("one"), false, null));
        asClient(appDataService.create(APP_CODE, SYSTEM, STORAGE_NAME, row("two"), false, null));

        assertEquals(2, documentsIn(LIVE_DB, UNIQUE_NAME).size());
        assertEquals(2, documentsIn(LIVE_DB, VERSION_COLLECTION).size(),
                "a versioned storage records one version row per create");

        Long deleted = asClient(appDataService.deleteByFilter(APP_CODE, SYSTEM, STORAGE_NAME, new Query(), false));

        assertEquals(2L, deleted);
        assertTrue(documentsIn(LIVE_DB, UNIQUE_NAME).isEmpty());

        // This is the whole difference from deleteStorage, which drops both.
        assertTrue(collectionsIn(LIVE_DB).contains(UNIQUE_NAME),
                "the collection must survive a clear, or its indexes go with it");
        assertEquals(2, documentsIn(LIVE_DB, VERSION_COLLECTION).size(),
                "clearing rows must not destroy the history of the rows that were there");
    }

    @Test
    @Timeout(60)
    @DisplayName("a dry run counts the rows and deletes nothing")
    void dryRunCountsOnly() {

        setInheritance(List.of(SYSTEM));
        storedStorage(false);

        asClient(appDataService.create(APP_CODE, SYSTEM, STORAGE_NAME, row("one"), false, null));
        asClient(appDataService.create(APP_CODE, SYSTEM, STORAGE_NAME, row("two"), false, null));

        Long counted = asClient(appDataService.deleteByFilter(APP_CODE, SYSTEM, STORAGE_NAME, new Query(), true));

        assertEquals(2L, counted);
        assertEquals(2, documentsIn(LIVE_DB, UNIQUE_NAME).size(), "a dry run must not delete");
    }

    @Test
    @Timeout(60)
    @DisplayName("clearing the draft surface leaves live rows alone")
    void clearOnDraftLeavesLive() {

        setInheritance(List.of(SYSTEM));
        storedStorage(false);

        asClient(appDataService.create(APP_CODE, SYSTEM, STORAGE_NAME, row("live row"), false, null));
        asDraftClient(appDataService.create(APP_CODE, SYSTEM, STORAGE_NAME, row("draft row"), false, null));

        Long deleted = asClient(appDataService.onSurface(APP_CODE, Boolean.TRUE,
                appDataService.deleteByFilter(APP_CODE, SYSTEM, STORAGE_NAME, new Query(), false)));

        assertEquals(1L, deleted);
        assertTrue(documentsIn(DRAFT_DB, UNIQUE_NAME).isEmpty());
        assertEquals(1, documentsIn(LIVE_DB, UNIQUE_NAME).size(),
                "clearing the sandbox must never reach live data");
    }

    // ── seeding draft from live ──────────────────────────────────────────────

    @Test
    @Timeout(60)
    @DisplayName("copying live to draft preserves ids and leaves live untouched")
    void copyPreservesIdsAndKeepsLive() {

        setInheritance(List.of(SYSTEM));
        storedStorage(false);

        asClient(appDataService.create(APP_CODE, SYSTEM, STORAGE_NAME, row("one"), false, null));
        asClient(appDataService.create(APP_CODE, SYSTEM, STORAGE_NAME, row("two"), false, null));

        Long copied = asClient(appDataService.copyLiveDataToDraft(APP_CODE, SYSTEM, STORAGE_NAME, true));

        assertEquals(2L, copied);

        List<Document> live = documentsIn(LIVE_DB, UNIQUE_NAME);
        List<Document> draft = documentsIn(DRAFT_DB, UNIQUE_NAME);

        assertEquals(2, live.size(), "the source must be untouched");
        assertEquals(2, draft.size());

        // Ids carried across is what keeps relation fields pointing at the right row
        // on both surfaces.
        assertEquals(live.stream().map(d -> d.get("_id")).sorted(idOrder()).toList(),
                draft.stream().map(d -> d.get("_id")).sorted(idOrder()).toList(),
                "draft rows must keep the ids their live originals had");
    }

    @Test
    @Timeout(60)
    @DisplayName("copying with replace empties the draft rows that were there first")
    void copyWithReplaceClearsDraftFirst() {

        setInheritance(List.of(SYSTEM));
        storedStorage(false);

        asClient(appDataService.create(APP_CODE, SYSTEM, STORAGE_NAME, row("live only"), false, null));
        asDraftClient(appDataService.create(APP_CODE, SYSTEM, STORAGE_NAME, row("sandbox scratch"), false, null));

        asClient(appDataService.copyLiveDataToDraft(APP_CODE, SYSTEM, STORAGE_NAME, true));

        List<Document> draft = documentsIn(DRAFT_DB, UNIQUE_NAME);
        assertEquals(1, draft.size(), "the pre-existing sandbox row must be gone, not added to");
        assertEquals("live only", draft.getFirst().get("title"));
    }

    @Test
    @Timeout(60)
    @DisplayName("copying twice is idempotent rather than a duplicate key failure")
    void copyTwiceIsIdempotent() {

        setInheritance(List.of(SYSTEM));
        storedStorage(false);

        asClient(appDataService.create(APP_CODE, SYSTEM, STORAGE_NAME, row("one"), false, null));

        // replace = false is the case an insertMany would fail on: the ids are
        // already there. It upserts instead.
        asClient(appDataService.copyLiveDataToDraft(APP_CODE, SYSTEM, STORAGE_NAME, false));
        Long second = asClient(appDataService.copyLiveDataToDraft(APP_CODE, SYSTEM, STORAGE_NAME, false));

        assertEquals(1L, second);
        assertEquals(1, documentsIn(DRAFT_DB, UNIQUE_NAME).size());
    }

    @Test
    @Timeout(60)
    @DisplayName("copying with no live rows is refused and changes nothing")
    void copyWithEmptySourceChangesNothing() {

        setInheritance(List.of(SYSTEM));
        storedStorage(false);

        asDraftClient(appDataService.create(APP_CODE, SYSTEM, STORAGE_NAME, row("sandbox scratch"), false, null));

        GenericException ex = assertThrows(GenericException.class,
                () -> asClient(appDataService.copyLiveDataToDraft(APP_CODE, SYSTEM, STORAGE_NAME, true)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());

        // The point of counting the source before clearing the destination: a
        // refusal that had already emptied the sandbox would be the worst of both.
        assertEquals(1, documentsIn(DRAFT_DB, UNIQUE_NAME).size(),
                "a refused copy must not have cleared the draft rows");
    }

    // ── the bar is the APP's, not the app's runtime roles ────────────────────
    //
    // Reported from the builder on 2026-09-03: seeding a sandbox on the `rim` app
    // answered 403. The cause was not the write-access gate, which passed -- it was
    // gating the copy on the storage's own createAuth, which on that storage reads
    // `Authorities.CXAPP.ROLE_Super_Admin`. A builder is not a user of the app and
    // holds no such authority, so the feature was dead for any app that sets one.
    // Clearing had the identical defect through deleteAuth.

    private static final String RUNTIME_ONLY_AUTH = "Authorities.CXAPP.ROLE_Super_Admin";

    @Test
    @Timeout(60)
    @DisplayName("a builder can seed a sandbox for a storage whose createAuth it cannot hold")
    void copyIgnoresRuntimeCreateAuth() {

        setInheritance(List.of(SYSTEM));
        storedStorage(false, RUNTIME_ONLY_AUTH);

        liveRow("live one");

        Long copied = asClient(appDataService.copyLiveDataToDraft(APP_CODE, SYSTEM, STORAGE_NAME, true));

        assertEquals(1L, copied, "app write access must be enough to seed the sandbox");
        assertEquals(1, documentsIn(DRAFT_DB, UNIQUE_NAME).size());
    }

    @Test
    @Timeout(60)
    @DisplayName("a builder can clear rows for a storage whose deleteAuth it cannot hold")
    void clearIgnoresRuntimeDeleteAuth() {

        setInheritance(List.of(SYSTEM));
        storedStorage(false, RUNTIME_ONLY_AUTH);

        liveRow("one");
        liveRow("two");

        Long cleared = asClient(appDataService.clearAllRows(APP_CODE, SYSTEM, STORAGE_NAME, false));

        assertEquals(2L, cleared, "app write access must be enough to clear rows");
        assertTrue(documentsIn(LIVE_DB, UNIQUE_NAME).isEmpty());
        assertTrue(collectionsIn(LIVE_DB).contains(UNIQUE_NAME));
    }

    @Test
    @Timeout(60)
    @DisplayName("clearing is still refused when neither bar is met")
    void clearNeedsOneOfTheTwoBars() {

        setInheritance(List.of(SYSTEM));
        storedStorage(false, RUNTIME_ONLY_AUTH);

        liveRow("one");

        // Neither the storage's deleteAuth (an app role nobody here holds) nor write
        // access to the application. The OR must not have become an "always".
        Mockito.when(this.feignSecurityService.hasWriteAccess(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Mono.just(Boolean.FALSE));

        GenericException ex = assertThrows(GenericException.class,
                () -> asClient(appDataService.clearAllRows(APP_CODE, SYSTEM, STORAGE_NAME, false)));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals(1, documentsIn(LIVE_DB, UNIQUE_NAME).size(), "a refused clear must delete nothing");
    }

    @Test
    @Timeout(60)
    @DisplayName("the storage's own delete authority still admits a runtime caller")
    void clearStillHonoursDeleteAuth() {

        setInheritance(List.of(SYSTEM));
        storedStorage(false, "Authorities.Storage_DELETE");

        liveRow("one");

        // The OR's other half: no app write access, but the caller holds exactly what
        // the storage asks for. Taking this away would leave the DROP route, which is
        // more destructive, with the looser gate.
        Mockito.when(this.feignSecurityService.hasWriteAccess(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Mono.just(Boolean.FALSE));

        Long cleared = asClient(appDataService.clearAllRows(APP_CODE, SYSTEM, STORAGE_NAME, false));

        assertEquals(1L, cleared);
        assertTrue(documentsIn(LIVE_DB, UNIQUE_NAME).isEmpty());
    }

    @Test
    @Timeout(60)
    @DisplayName("copying to draft without write access on the app is forbidden")
    void copyNeedsWriteAccess() {

        setInheritance(List.of(SYSTEM));
        storedStorage(false);

        asClient(appDataService.create(APP_CODE, SYSTEM, STORAGE_NAME, row("one"), false, null));

        Mockito.when(this.feignSecurityService.hasWriteAccess(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Mono.just(Boolean.FALSE));

        GenericException ex = assertThrows(GenericException.class,
                () -> asClient(appDataService.copyLiveDataToDraft(APP_CODE, SYSTEM, STORAGE_NAME, true)));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    private static java.util.Comparator<Object> idOrder() {
        return java.util.Comparator.comparing(Object::toString);
    }
}
