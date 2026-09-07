package com.fincity.saas.ui.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.ui.document.Page;
import com.fincity.saas.ui.service.EngineService;
import com.fincity.saas.ui.service.PageService;

import reactor.core.publisher.Mono;

/**
 * The draft surface: the same runtime read path, switched by the gateway-set
 * flag alone.
 *
 * These are the tests that matter for the security property. A draft must never
 * appear on the live surface, and the flag must be the only thing that changes
 * the answer.
 */
@DisplayName("Draft surface")
class DraftSurfaceIntegrationTest extends AbstractIntegrationTest {

    private static final String PAGE_NAME = "testPage";

    @Autowired
    private PageService pageService;

    @Autowired
    private EngineService engineService;

    private Page storedPage(String clientCode, Map<String, Object> properties) {
        Page page = new Page();
        page.setName(PAGE_NAME)
                .setAppCode(APP_CODE)
                .setClientCode(clientCode)
                .setVersion(1);
        page.setProperties(properties == null ? null : new HashMap<>(properties));
        page.setRootComponent("rootComp");
        return this.insertRaw(page);
    }

    private static Map<String, Object> props(Object... keyValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2)
            map.put((String) keyValues[i], keyValues[i + 1]);
        return map;
    }

    private <T> T asClient(Mono<T> mono, String clientCode) {
        ContextAuthentication ca = this.authFor(clientCode, allAuthoritiesFor("Page"));
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca)).block();
    }

    private <T> T asDraftClient(Mono<T> mono, String clientCode) {
        ContextAuthentication ca = this.authFor(clientCode, allAuthoritiesFor("Page"));
        return this.onDraftSurface(mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca))).block();
    }

    private Page draftedPage(String title) {
        Page live = storedPage(SYSTEM, props("title", "live"));
        Page edited = asClient(pageService.read(live.getId()), SYSTEM);
        edited.getProperties().put("title", title);
        asClient(pageService.saveDraft(edited), SYSTEM);
        return live;
    }

    @Nested
    @DisplayName("the runtime read")
    class RuntimeRead {

        @Test
        @Timeout(30)
        @DisplayName("serves the draft on the draft surface and live everywhere else")
        void servesDraftOnlyOnDraftSurface() {

            setInheritance(List.of(SYSTEM));
            draftedPage("drafted");

            var live = asClient(pageService.read(PAGE_NAME, APP_CODE, SYSTEM), SYSTEM);
            assertNotNull(live);
            assertEquals("live", live.getObject().getProperties().get("title"));

            var draft = asDraftClient(pageService.read(PAGE_NAME, APP_CODE, SYSTEM), SYSTEM);
            assertNotNull(draft);
            assertEquals("drafted", draft.getObject().getProperties().get("title"));
        }

        @Test
        @Timeout(30)
        @DisplayName("falls back to live content where no draft exists")
        void fallsBackToLive() {

            setInheritance(List.of(SYSTEM));
            storedPage(SYSTEM, props("title", "live"));

            var draft = asDraftClient(pageService.read(PAGE_NAME, APP_CODE, SYSTEM), SYSTEM);
            assertNotNull(draft);
            assertEquals("live", draft.getObject().getProperties().get("title"));
        }

        @Test
        @Timeout(30)
        @DisplayName("shows never-published objects, which the live surface hides")
        void showsUnpublished() {

            setInheritance(List.of(SYSTEM));
            Page unpublished = storedPage(SYSTEM, props("title", "wip"));
            mongoTemplate.save(unpublished.setPublished(Boolean.FALSE)).block();

            assertNull(asClient(pageService.read(PAGE_NAME, APP_CODE, SYSTEM), SYSTEM),
                    "the live surface must not serve an unpublished page");

            var draft = asDraftClient(pageService.read(PAGE_NAME, APP_CODE, SYSTEM), SYSTEM);
            assertNotNull(draft, "the draft surface exists precisely to show unpublished work");
            assertEquals("wip", draft.getObject().getProperties().get("title"));
        }
    }

    @Nested
    @DisplayName("across the override chain")
    class OverrideChainIsolation {

        private static final String MID = "LZCLA";
        private static final String LEAF = "LZACP1";

        private Page storedFor(String clientCode, String baseClientCode, String title) {
            Page page = new Page();
            page.setName(PAGE_NAME)
                    .setAppCode(APP_CODE)
                    .setClientCode(clientCode)
                    .setBaseClientCode(baseClientCode)
                    .setVersion(1);
            page.setProperties(props("title", title));
            page.setRootComponent("rootComp");
            return insertRaw(page);
        }

        private void draftOn(Page page, String clientCode, String title) {
            Page edited = asClient(pageService.read(page.getId()), clientCode);
            edited.getProperties().put("title", title);
            asClient(pageService.saveDraft(edited), clientCode);
        }

        @Test
        @Timeout(30)
        @DisplayName("a base client's draft is NOT served to a derived client with no override")
        void baseDraftNotServedToDerived() {

            setInheritance(List.of(SYSTEM, MID));
            Page base = storedFor(SYSTEM, null, "base live");
            draftOn(base, SYSTEM, "base draft");

            // MID has no override of its own, so readIfExistsInBase resolves to the
            // base document. The base is usually SYSTEM, so leaking here means every
            // tenant sees the platform's unpublished work.
            var read = asDraftClient(pageService.read(PAGE_NAME, APP_CODE, MID), MID);
            assertNotNull(read);
            assertEquals("base live", read.getObject().getProperties().get("title"),
                    "a derived client's draft surface served the base client's draft");
        }

        @Test
        @Timeout(30)
        @DisplayName("the base client still sees its own draft on its own surface")
        void baseSeesOwnDraft() {

            setInheritance(List.of(SYSTEM));
            Page base = storedFor(SYSTEM, null, "base live");
            draftOn(base, SYSTEM, "base draft");

            var read = asDraftClient(pageService.read(PAGE_NAME, APP_CODE, SYSTEM), SYSTEM);
            assertNotNull(read);
            assertEquals("base draft", read.getObject().getProperties().get("title"));
        }

        @Test
        @Timeout(30)
        @DisplayName("a derived client with its own override still sees its own draft")
        void derivedSeesOwnDraft() {

            setInheritance(List.of(SYSTEM, MID));
            storedFor(SYSTEM, null, "base live");
            Page mid = storedFor(MID, SYSTEM, "mid live");
            draftOn(mid, MID, "mid draft");

            // The guard must not over-correct into "no draft is ever substituted".
            var read = asDraftClient(pageService.read(PAGE_NAME, APP_CODE, MID), MID);
            assertNotNull(read);
            assertEquals("mid draft", read.getObject().getProperties().get("title"));
        }

        @Test
        @Timeout(30)
        @DisplayName("three deep: a mid draft does not reach a leaf with no override")
        void midDraftNotServedToLeaf() {

            setInheritance(List.of(SYSTEM, MID, LEAF));
            storedFor(SYSTEM, null, "base live");
            Page mid = storedFor(MID, SYSTEM, "mid live");
            draftOn(mid, MID, "mid draft");

            var read = asDraftClient(pageService.read(PAGE_NAME, APP_CODE, LEAF), LEAF);
            assertNotNull(read);
            assertEquals("mid live", read.getObject().getProperties().get("title"),
                    "the leaf's draft surface served an ancestor's draft");
        }
    }

    @Nested
    @DisplayName("caching")
    class Caching {

        @Test
        @Timeout(30)
        @DisplayName("a draft read does not poison the live definition cache")
        void draftDoesNotPoisonLiveCache() {

            setInheritance(List.of(SYSTEM));
            draftedPage("drafted");

            // Draft first, so if the two shared a cache the live read would be the
            // one that got the wrong answer.
            var draft = asDraftClient(pageService.read(PAGE_NAME, APP_CODE, SYSTEM), SYSTEM);
            assertNotNull(draft);
            assertEquals("drafted", draft.getObject().getProperties().get("title"));

            var live = asClient(pageService.read(PAGE_NAME, APP_CODE, SYSTEM), SYSTEM);
            assertNotNull(live);
            assertEquals("live", live.getObject().getProperties().get("title"));
        }

        @Test
        @Timeout(30)
        @DisplayName("a live read does not poison the draft definition cache")
        void liveDoesNotPoisonDraftCache() {

            setInheritance(List.of(SYSTEM));
            draftedPage("drafted");

            var live = asClient(pageService.read(PAGE_NAME, APP_CODE, SYSTEM), SYSTEM);
            assertNotNull(live);
            assertEquals("live", live.getObject().getProperties().get("title"));

            var draft = asDraftClient(pageService.read(PAGE_NAME, APP_CODE, SYSTEM), SYSTEM);
            assertNotNull(draft);
            assertEquals("drafted", draft.getObject().getProperties().get("title"));
        }
    }

    @Nested
    @DisplayName("the engine response")
    class EngineResponse {

        @Test
        @Timeout(30)
        @DisplayName("carries a distinct ETag and no-store on the draft surface")
        void distinctETagAndNoStore() {

            setInheritance(List.of(SYSTEM));
            draftedPage("drafted");

            ResponseEntity<Page> liveResp = asClient(
                    engineService.readPage(null, PAGE_NAME, APP_CODE, SYSTEM), SYSTEM);
            ResponseEntity<Page> draftResp = asDraftClient(
                    engineService.readPage(null, PAGE_NAME, APP_CODE, SYSTEM), SYSTEM);

            assertNotNull(liveResp);
            assertNotNull(draftResp);

            String liveTag = liveResp.getHeaders().getFirst("ETag");
            String draftTag = draftResp.getHeaders().getFirst("ETag");
            assertNotNull(liveTag);
            assertNotNull(draftTag);
            assertNotEquals(liveTag, draftTag,
                    "a shared ETag would let a browser or proxy serve one surface's page for the other");
            assertTrue(draftTag.contains("dlg-") || draftTag.contains("dnlg-"),
                    "the draft marker must be part of the ETag, found: " + draftTag);

            assertEquals("no-store", draftResp.getHeaders().getFirst("Cache-Control"),
                    "a draft must not be cached by the browser for the live seven-day window");
            assertTrue(liveResp.getHeaders().getFirst("Cache-Control").startsWith("max-age"));
        }

        @Test
        @Timeout(30)
        @DisplayName("returns the right body on each surface")
        void rightBodyPerSurface() {

            setInheritance(List.of(SYSTEM));
            draftedPage("drafted");

            ResponseEntity<Page> liveResp = asClient(
                    engineService.readPage(null, PAGE_NAME, APP_CODE, SYSTEM), SYSTEM);
            ResponseEntity<Page> draftResp = asDraftClient(
                    engineService.readPage(null, PAGE_NAME, APP_CODE, SYSTEM), SYSTEM);

            assertNotNull(liveResp.getBody());
            assertNotNull(draftResp.getBody());
            assertEquals("live", liveResp.getBody().getProperties().get("title"));
            assertEquals("drafted", draftResp.getBody().getProperties().get("title"));
        }
    }

    @Nested
    @DisplayName("publishing from the draft surface")
    class Publishing {

        @Test
        @Timeout(30)
        @DisplayName("makes both surfaces agree")
        void bothSurfacesAgree() {

            setInheritance(List.of(SYSTEM));
            Page live = draftedPage("drafted");

            asClient(pageService.publish(live.getId(), "ship"), SYSTEM);

            var liveRead = asClient(pageService.read(PAGE_NAME, APP_CODE, SYSTEM), SYSTEM);
            var draftRead = asDraftClient(pageService.read(PAGE_NAME, APP_CODE, SYSTEM), SYSTEM);

            assertNotNull(liveRead);
            assertNotNull(draftRead);
            assertEquals("drafted", liveRead.getObject().getProperties().get("title"));
            assertEquals("drafted", draftRead.getObject().getProperties().get("title"),
                    "the draft cache must be evicted on publish, or it keeps serving what is now live anyway");
        }

        @Test
        @Timeout(30)
        @DisplayName("evicts a draft surface read cached before the publish")
        void evictsPreviouslyCachedDraft() {

            setInheritance(List.of(SYSTEM));
            Page live = draftedPage("drafted");

            // Warm the draft cache first.
            ObjectWithUniqueID<Page> before = asDraftClient(
                    pageService.read(PAGE_NAME, APP_CODE, SYSTEM), SYSTEM);
            assertNotNull(before);

            asClient(pageService.publish(live.getId(), "ship"), SYSTEM);

            var after = asDraftClient(pageService.read(PAGE_NAME, APP_CODE, SYSTEM), SYSTEM);
            assertNotNull(after);
            assertEquals("drafted", after.getObject().getProperties().get("title"));
        }
    }
}
