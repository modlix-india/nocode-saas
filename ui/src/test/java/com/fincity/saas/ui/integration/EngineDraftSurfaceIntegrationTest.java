package com.fincity.saas.ui.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.ui.document.Application;
import com.fincity.saas.ui.service.ApplicationService;
import com.fincity.saas.ui.service.EngineService;

import reactor.core.publisher.Mono;

/**
 * The runtime read path is draft-aware for the whole app, not just for pages.
 *
 * readPage was made draft-aware when the surface was built and the other four
 * runtime reads were not, which is a bad shape to leave: the browser would fetch a
 * drafted page and then the LIVE application definition, styles, theme and
 * functions to render it with, each cached for seven days with no draft marker on
 * the ETag. So a drafted theme or app property did nothing at all on the draft
 * surface, and whichever surface was read first won the shared cache entry for both.
 *
 * The application read is the one that mattered most, because ApplicationService
 * .readProperties is what the client uses for the page list, theme and app-level
 * settings.
 */
@DisplayName("Engine reads on the draft surface")
class EngineDraftSurfaceIntegrationTest extends AbstractIntegrationTest {

    private static final String APP_NAME = "testapp";

    @Autowired
    private EngineService engineService;

    @Autowired
    private ApplicationService applicationService;

    private ContextAuthentication ca() {
        return this.authFor(SYSTEM, allAuthoritiesFor("Application"));
    }

    private <T> T live(Mono<T> mono) {
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca())).block();
    }

    private <T> T draft(Mono<T> mono) {
        return this.onDraftSurface(mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca())))
                .block();
    }

    private Application storedApp(String title) {

        Application app = new Application();
        app.setName(APP_NAME).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        Map<String, Object> properties = new HashMap<>();
        properties.put("title", title);
        app.setProperties(properties);
        return this.insertRaw(app);
    }

    private void draftTheApp(Application stored, String title) {

        Application edit = new Application();
        edit.setId(stored.getId());
        edit.setName(APP_NAME).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        Map<String, Object> properties = new HashMap<>();
        properties.put("title", title);
        edit.setProperties(properties);

        assertNotNull(live(this.applicationService.saveDraft(edit)));
    }

    @Test
    @Timeout(60)
    @DisplayName("readProperties serves the draft on the draft surface and the live one on live")
    void readPropertiesFollowsTheSurface() {

        setInheritance(List.of(SYSTEM));
        Application stored = storedApp("liveTitle");
        draftTheApp(stored, "draftTitle");

        // The draft surface first, deliberately. Reading live first and then draft
        // would pass even with one shared cache entry, because the second read would
        // find the entry the first one wrote and the values would differ anyway.
        // This order is the one that catches a missing cache dimension.
        Map<String, Object> onDraft = draft(this.applicationService.readProperties(APP_NAME, APP_CODE, SYSTEM));
        assertEquals("draftTitle", onDraft.get("title"),
                "the drafted app definition did nothing on the draft surface");

        Map<String, Object> onLive = live(this.applicationService.readProperties(APP_NAME, APP_CODE, SYSTEM));
        assertEquals("liveTitle", onLive.get("title"),
                "the draft leaked onto the live surface through a shared cache entry");
    }

    @Test
    @Timeout(60)
    @DisplayName("and the other way round, so neither surface poisons the other's cache")
    void readPropertiesLiveFirst() {

        setInheritance(List.of(SYSTEM));
        Application stored = storedApp("liveTitle");
        draftTheApp(stored, "draftTitle");

        assertEquals("liveTitle", live(this.applicationService.readProperties(APP_NAME, APP_CODE, SYSTEM))
                .get("title"));
        assertEquals("draftTitle", draft(this.applicationService.readProperties(APP_NAME, APP_CODE, SYSTEM))
                .get("title"));
    }

    @Test
    @Timeout(60)
    @DisplayName("the application read is no-store on draft and cacheable on live")
    void applicationCacheHeaders() {

        setInheritance(List.of(SYSTEM));
        storedApp("liveTitle");

        Mockito.when(this.feignAuthenticationService.getAppStatusByCode(Mockito.anyString()))
                .thenReturn(Mono.just("ACTIVE"));

        ResponseEntity<Application> onDraft = draft(this.engineService.readApplication(null, APP_CODE, SYSTEM));
        ResponseEntity<Application> onLive = live(this.engineService.readApplication(null, APP_CODE, SYSTEM));

        assertEquals("no-store", onDraft.getHeaders().getFirst("Cache-Control"),
                "a draft app definition was handed to the browser with a seven day cache life");
        assertTrue(onLive.getHeaders().getFirst("Cache-Control").contains("max-age"),
                "the live response stopped being cacheable: " + onLive.getHeaders().getFirst("Cache-Control"));
    }

    @Test
    @Timeout(60)
    @DisplayName("the two surfaces never share an ETag, so a browser cannot replay one for the other")
    void eTagsCarryTheSurface() {

        setInheritance(List.of(SYSTEM));
        storedApp("liveTitle");

        Mockito.when(this.feignAuthenticationService.getAppStatusByCode(Mockito.anyString()))
                .thenReturn(Mono.just("ACTIVE"));

        String draftTag = draft(this.engineService.readApplication(null, APP_CODE, SYSTEM))
                .getHeaders().getFirst("ETag");
        String liveTag = live(this.engineService.readApplication(null, APP_CODE, SYSTEM))
                .getHeaders().getFirst("ETag");

        assertNotNull(draftTag);
        assertNotNull(liveTag);
        assertNotEquals(liveTag, draftTag, "both surfaces issued the same ETag for the same object");
        assertTrue(draftTag.contains("d-"), "the draft ETag carries no surface marker: " + draftTag);
    }

    @Test
    @Timeout(60)
    @DisplayName("theme and style responses are no-store on the draft surface too")
    void themeAndStyleHeaders() {

        setInheritance(List.of(SYSTEM));
        storedApp("liveTitle");

        // Null theme: the app's default, which is what the runtime asks for when the
        // visitor has picked none. The named-theme case is not what this is about.
        ResponseEntity<Map<String, Map<String, String>>> theme = draft(
                this.engineService.readTheme(null, APP_CODE, SYSTEM, null));
        ResponseEntity<String> style = draft(this.engineService.readStyle(null, APP_CODE, SYSTEM, null));

        assertEquals("no-store", theme.getHeaders().getFirst("Cache-Control"));
        assertEquals("no-store", style.getHeaders().getFirst("Cache-Control"));
    }
}
