package com.fincity.saas.ui.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.ui.document.Application;
import com.fincity.saas.ui.document.Page;
import com.fincity.saas.ui.service.ApplicationService;
import com.fincity.saas.ui.service.EngineService;
import com.fincity.saas.ui.service.PageService;

import reactor.core.publisher.Mono;

/**
 * A draft SAVE has to clear the draft surface's caches.
 *
 * Every other draft test writes the draft before the draft surface is ever read,
 * so all of them passed against a saveDraft that evicted nothing at all. The order
 * here is the one that matters and the one a person actually produces: open the
 * draft link, then edit and save, then look again. The draft surface's caches are
 * filled by that first READ, not by the save, so nothing invalidated them and the
 * draft host went on answering with pre-draft content indefinitely.
 *
 * Found on appbuilder, where a draft moving `defaultPage` to `builderHome` kept
 * being served as `landing` until `ApplicationCache_appbuilder_appbuilder_DRAFT`
 * was evicted by hand.
 *
 * The reverse assertion is in every test too: the live surface must still answer
 * live. Eviction is a cache miss, never a route to the other surface's content.
 */
@DisplayName("A draft save evicts the draft surface")
class DraftSaveEvictionIntegrationTest extends AbstractIntegrationTest {

    private static final String APP_NAME = APP_CODE;
    private static final String PAGE_NAME = "testPage";

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private PageService pageService;

    @Autowired
    private EngineService engineService;

    private <T> T live(Mono<T> mono, String objectName) {
        ContextAuthentication ca = this.authFor(SYSTEM, allAuthoritiesFor(objectName));
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca)).block();
    }

    private <T> T draft(Mono<T> mono, String objectName) {
        ContextAuthentication ca = this.authFor(SYSTEM, allAuthoritiesFor(objectName));
        return this.onDraftSurface(mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca))).block();
    }

    private Application storedApp(String title) {

        Application app = new Application();
        app.setName(APP_NAME).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        Map<String, Object> properties = new HashMap<>();
        properties.put("title", title);
        app.setProperties(properties);
        return this.insertRaw(app);
    }

    private void draftTheApp(String id, String title) {

        Application edit = new Application();
        edit.setId(id);
        edit.setName(APP_NAME).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        Map<String, Object> properties = new HashMap<>();
        properties.put("title", title);
        edit.setProperties(properties);

        assertNotNull(live(this.applicationService.saveDraft(edit), "Application"));
    }

    private Page storedPage(String title) {

        Page page = new Page();
        page.setName(PAGE_NAME).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        Map<String, Object> properties = new HashMap<>();
        properties.put("title", title);
        page.setProperties(properties);
        page.setRootComponent("rootComp");
        return this.insertRaw(page);
    }

    @Test
    @Timeout(60)
    @DisplayName("readProperties: a draft saved after the surface was read is served, not the cached live copy")
    void appPropertiesAfterAReadThenASave() {

        setInheritance(List.of(SYSTEM));
        Application stored = storedApp("liveTitle");

        // Fills ApplicationCache_<app>_<app>_DRAFT and cacheProperties_<app>_DRAFT
        // with the live document, because no draft exists yet.
        assertEquals("liveTitle",
                draft(this.applicationService.readProperties(APP_NAME, APP_CODE, SYSTEM), "Application").get("title"));

        draftTheApp(stored.getId(), "draftTitle");

        assertEquals("draftTitle",
                draft(this.applicationService.readProperties(APP_NAME, APP_CODE, SYSTEM), "Application").get("title"),
                "the draft host kept serving pre-draft content: the save evicted no draft cache");

        assertEquals("liveTitle",
                live(this.applicationService.readProperties(APP_NAME, APP_CODE, SYSTEM), "Application").get("title"),
                "the draft leaked onto the live surface");
    }

    @Test
    @Timeout(60)
    @DisplayName("the runtime application read picks the save up too")
    void engineApplicationAfterAReadThenASave() {

        setInheritance(List.of(SYSTEM));
        Mockito.when(this.feignAuthenticationService.getAppStatusByCode(Mockito.anyString()))
                .thenReturn(Mono.just("ACTIVE"));

        Application stored = storedApp("liveTitle");

        assertEquals("liveTitle", draft(this.engineService.readApplication(null, APP_CODE, SYSTEM), "Application")
                .getBody().getProperties().get("title"));

        draftTheApp(stored.getId(), "draftTitle");

        assertEquals("draftTitle", draft(this.engineService.readApplication(null, APP_CODE, SYSTEM), "Application")
                .getBody().getProperties().get("title"),
                "api/ui/application on the draft host answered from a cache the save never cleared");

        assertEquals("liveTitle", live(this.engineService.readApplication(null, APP_CODE, SYSTEM), "Application")
                .getBody().getProperties().get("title"));
    }

    @Test
    @Timeout(60)
    @DisplayName("pages behave the same way")
    void pageAfterAReadThenASave() {

        setInheritance(List.of(SYSTEM));
        Page stored = storedPage("liveTitle");

        assertEquals("liveTitle",
                draft(this.pageService.read(PAGE_NAME, APP_CODE, SYSTEM), "Page").getObject().getProperties()
                        .get("title"));

        Page edit = live(this.pageService.read(stored.getId()), "Page");
        edit.getProperties().put("title", "draftTitle");
        assertNotNull(live(this.pageService.saveDraft(edit), "Page"));

        assertEquals("draftTitle",
                draft(this.pageService.read(PAGE_NAME, APP_CODE, SYSTEM), "Page").getObject().getProperties()
                        .get("title"),
                "the drafted page was cached away behind the read that preceded the save");

        assertEquals("liveTitle",
                live(this.pageService.read(PAGE_NAME, APP_CODE, SYSTEM), "Page").getObject().getProperties()
                        .get("title"));
    }

    @Test
    @Timeout(60)
    @DisplayName("a second save over an existing draft is seen as well")
    void secondSaveOverAnExistingDraft() {

        setInheritance(List.of(SYSTEM));
        Application stored = storedApp("liveTitle");

        draftTheApp(stored.getId(), "first");
        assertEquals("first",
                draft(this.applicationService.readProperties(APP_NAME, APP_CODE, SYSTEM), "Application").get("title"));

        draftTheApp(stored.getId(), "second");
        assertEquals("second",
                draft(this.applicationService.readProperties(APP_NAME, APP_CODE, SYSTEM), "Application").get("title"),
                "the draft surface froze on the first draft it ever served");
    }

    @Test
    @Timeout(60)
    @DisplayName("discarding a draft brings the live document back on the draft surface")
    void discardIsSeenToo() {

        setInheritance(List.of(SYSTEM));
        Application stored = storedApp("liveTitle");

        draftTheApp(stored.getId(), "draftTitle");
        assertEquals("draftTitle",
                draft(this.applicationService.readProperties(APP_NAME, APP_CODE, SYSTEM), "Application").get("title"));

        assertNotNull(live(this.applicationService.discardDraft(stored.getId()), "Application"));

        assertEquals("liveTitle",
                draft(this.applicationService.readProperties(APP_NAME, APP_CODE, SYSTEM), "Application").get("title"),
                "the discarded draft was still being served from the draft cache");
    }
}
