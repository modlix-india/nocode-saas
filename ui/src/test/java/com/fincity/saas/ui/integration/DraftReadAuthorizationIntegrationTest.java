package com.fincity.saas.ui.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.ui.document.Page;
import com.fincity.saas.ui.service.PageService;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

/**
 * Whose draft the by-id editor route is allowed to hand back.
 *
 * readDraftWithVersion ran read(id) and then substituted the draft with no check
 * on WHOSE draft it was. read()'s check is the READ one, and an override chain
 * grants a derived client read access to every ancestor's document, so
 * GET /api/ui/pages/{baseId}?draft=true returned the BASE client's unpublished
 * work to any derived client. The base is usually SYSTEM.
 *
 * readDrafted() guards exactly this on the runtime path and says so in its
 * comment; the by-id path never got the same guard. Cross-client editing makes a
 * derived client holding an ancestor's id the normal case, so this goes from an
 * oddity to the common request.
 *
 * It also poisons the version protocol: the derived client would send the base's
 * non-zero draft version back on save and take a spurious 412.
 */
@DisplayName("Draft read authorization")
class DraftReadAuthorizationIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "LZBASE";
    private static final String DERIVED = "LZDERIV";
    private static final String MANAGER = "LZMGR";

    private static final String PAGE_NAME = "testPage";

    @Autowired
    private PageService pageService;

    private Page storedPage(String clientCode, String baseClientCode, Map<String, Object> properties) {
        Page page = new Page();
        page.setName(PAGE_NAME)
                .setAppCode(APP_CODE)
                .setClientCode(clientCode)
                .setBaseClientCode(baseClientCode)
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

    /** Nobody manages anybody, except the manager. */
    private void isolateTenants() {
        Mockito.when(this.feignAuthenticationService.doesClientManageClientCode(Mockito.anyString(),
                Mockito.anyString())).thenReturn(Mono.just(Boolean.FALSE));
        Mockito.when(this.feignAuthenticationService.doesClientManageClientCode(Mockito.eq(MANAGER),
                Mockito.anyString())).thenReturn(Mono.just(Boolean.TRUE));
    }

    @Nested
    @DisplayName("a derived client reading its base's object by id")
    class DerivedReadingBase {

        @Test
        @Timeout(30)
        @DisplayName("gets the live document, never the base's unpublished draft")
        void doesNotServeAncestorsDraft() {

            setInheritance(List.of(BASE, DERIVED));
            Page basePage = storedPage(BASE, null, props("title", "base live"));

            // The base has real unpublished work.
            Page baseEdit = asClient(pageService.read(basePage.getId()), BASE);
            baseEdit.getProperties().put("title", "base UNPUBLISHED secret");
            asClient(pageService.saveDraft(baseEdit), BASE);

            isolateTenants();

            Tuple2<Page, Integer> read = asClient(pageService.readDraftWithVersion(basePage.getId()), DERIVED);

            assertNotNull(read);
            assertEquals("base live", read.getT1().getProperties().get("title"),
                    "a derived client was handed the base client's unpublished draft");
        }

        @Test
        @Timeout(30)
        @DisplayName("gets draft version 0, so its next save is not a spurious 412")
        void reportsVersionZero() {

            setInheritance(List.of(BASE, DERIVED));
            Page basePage = storedPage(BASE, null, props("title", "base live"));

            Page baseEdit = asClient(pageService.read(basePage.getId()), BASE);
            baseEdit.getProperties().put("title", "base UNPUBLISHED secret");
            asClient(pageService.saveDraft(baseEdit), BASE);

            isolateTenants();

            Tuple2<Page, Integer> read = asClient(pageService.readDraftWithVersion(basePage.getId()), DERIVED);

            assertNotNull(read);
            assertEquals(0, read.getT2(),
                    "the derived client would send the base's draft version back and take a 412");
        }
    }

    @Nested
    @DisplayName("the cases that must keep working")
    class StillWorks {

        @Test
        @Timeout(30)
        @DisplayName("a client still reads its own draft")
        void ownDraftStillServed() {

            setInheritance(List.of(BASE));
            Page basePage = storedPage(BASE, null, props("title", "base live"));

            Page edit = asClient(pageService.read(basePage.getId()), BASE);
            edit.getProperties().put("title", "my draft");
            asClient(pageService.saveDraft(edit), BASE);

            Tuple2<Page, Integer> read = asClient(pageService.readDraftWithVersion(basePage.getId()), BASE);

            assertNotNull(read);
            assertEquals("my draft", read.getT1().getProperties().get("title"),
                    "the guard locked a client out of its own draft");
            assertEquals(1, read.getT2(), "the draft's own version must come back for the concurrency check");
        }

        /**
         * The manager must be in the chain for read(id) to resolve the base's
         * document at all -- that check is the READ one and predates this work, so a
         * manager outside the chain never reached the draft logic either way. With
         * the chain in place the ONLY thing separating this from the derived-client
         * case above is doesClientManageClientCode, which is precisely the guard.
         */
        @Test
        @Timeout(30)
        @DisplayName("a managing client still reads the managed client's draft")
        void managerStillServed() {

            setInheritance(List.of(BASE, MANAGER));
            Page basePage = storedPage(BASE, null, props("title", "base live"));

            Page edit = asClient(pageService.read(basePage.getId()), BASE);
            edit.getProperties().put("title", "managed draft");
            asClient(pageService.saveDraft(edit), BASE);

            isolateTenants();

            Tuple2<Page, Integer> read = asClient(pageService.readDraftWithVersion(basePage.getId()), MANAGER);

            assertNotNull(read);
            assertEquals("managed draft", read.getT1().getProperties().get("title"),
                    "the guard broke the supported manager case");
        }
    }
}
