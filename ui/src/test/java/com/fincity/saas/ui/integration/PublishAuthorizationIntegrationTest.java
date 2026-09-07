package com.fincity.saas.ui.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.ui.document.Page;
import com.fincity.saas.ui.service.PageService;
import com.fincity.saas.ui.service.PublishService;

import reactor.core.publisher.Mono;

/**
 * Authorization on the app-level publish endpoints.
 *
 * Every `ui` route is permitAll at the HTTP layer: UIConfiguration passes "/**"
 * as its exclusion list, so all authorization must come from the service. These
 * two methods had none, and `pending` returned the objectId of every unpublished
 * object for any app and any client, to anyone. Those ids are what make writing
 * into another tenant's draft possible, so this is not merely an information leak.
 */
@DisplayName("Publish endpoint authorization")
class PublishAuthorizationIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER = "LZCLA";
    private static final String OUTSIDER = "LZCLB";
    private static final String PAGE_NAME = "testPage";

    @Autowired
    private PageService pageService;

    @Autowired
    private PublishService publishService;

    private Page storedPage(String clientCode) {
        Page page = new Page();
        page.setName(PAGE_NAME)
                .setAppCode(APP_CODE)
                .setClientCode(clientCode)
                .setVersion(1);
        Map<String, Object> p = new HashMap<>();
        p.put("title", "live");
        page.setProperties(p);
        page.setRootComponent("rootComp");
        return this.insertRaw(page);
    }

    private <T> T asClient(Mono<T> mono, String clientCode) {
        ContextAuthentication ca = this.authFor(clientCode, allAuthoritiesFor("Page"));
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca)).block();
    }

    private void seedPendingDraft() {
        setInheritance(List.of(OWNER));
        Page page = storedPage(OWNER);
        Page edited = asClient(pageService.read(page.getId()), OWNER);
        edited.getProperties().put("title", "drafted");
        asClient(pageService.saveDraft(edited), OWNER);
    }

    private void isolateTenants() {
        Mockito.when(this.feignAuthenticationService.doesClientManageClientCode(Mockito.anyString(),
                Mockito.anyString())).thenReturn(Mono.just(Boolean.FALSE));
    }

    private boolean refused(Runnable action) {
        try {
            action.run();
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("pending is refused without authentication")
    void pendingRefusesAnonymous() {

        seedPendingDraft();

        assertTrue(refused(() -> publishService.pending(APP_CODE, OWNER).block()),
                "an unauthenticated caller listed every pending draft in the app");
    }

    @Test
    @Timeout(30)
    @DisplayName("publishAll is refused without authentication")
    void publishAllRefusesAnonymous() {

        seedPendingDraft();

        assertTrue(refused(() -> publishService.publishAll(APP_CODE, OWNER).block()));
    }

    @Test
    @Timeout(30)
    @DisplayName("pending refuses a clientCode the caller does not manage")
    void pendingRefusesUnmanagedClient() {

        seedPendingDraft();
        isolateTenants();

        assertTrue(refused(() -> asClient(publishService.pending(APP_CODE, OWNER), OUTSIDER)),
                "a supplied clientCode was trusted verbatim");
    }

    @Test
    @Timeout(30)
    @DisplayName("a refused call discloses no objectId")
    void refusedCallLeaksNoObjectIds() {

        seedPendingDraft();
        isolateTenants();

        Map<String, List<Map<String, Object>>> result = null;
        try {
            result = asClient(publishService.pending(APP_CODE, OWNER), OUTSIDER);
        } catch (Exception e) {
            // expected
        }

        // The ids are what make cross-tenant draft writes possible, so an empty map
        // would be acceptable but a populated one would not.
        assertTrue(result == null || result.isEmpty(),
                "a refused listing still returned objectIds: " + result);
    }

    @Test
    @Timeout(30)
    @DisplayName("pending with no clientCode resolves from the context")
    void pendingResolvesFromContext() {

        seedPendingDraft();

        var result = asClient(publishService.pending(APP_CODE, null), OWNER);
        assertNotNull(result);
        assertEquals(1, result.getOrDefault("PAGE", List.of()).size());
    }

    @Test
    @Timeout(30)
    @DisplayName("listing needs read access, publishing needs write")
    void publishNeedsWriteWhereListNeedsRead() {

        seedPendingDraft();

        Mockito.when(this.feignAuthenticationService.hasWriteAccess(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Mono.just(Boolean.FALSE));

        var listed = asClient(publishService.pending(APP_CODE, OWNER), OWNER);
        assertNotNull(listed, "read access should still allow listing");

        assertTrue(refused(() -> asClient(publishService.publishAll(APP_CODE, OWNER), OWNER)),
                "a read-only caller was able to publish");
    }

    @Test
    @Timeout(30)
    @DisplayName("an authorized caller can still list and publish")
    void authorizedCallerWorks() {

        seedPendingDraft();

        var listed = asClient(publishService.pending(APP_CODE, OWNER), OWNER);
        assertNotNull(listed);
        assertEquals(1, listed.getOrDefault("PAGE", List.of()).size());

        var published = asClient(publishService.publishAll(APP_CODE, OWNER), OWNER);
        assertNotNull(published);
        assertEquals(1L, published.get("published"));
        assertFalse(((List<?>) published.get("results")).isEmpty());
    }
}
