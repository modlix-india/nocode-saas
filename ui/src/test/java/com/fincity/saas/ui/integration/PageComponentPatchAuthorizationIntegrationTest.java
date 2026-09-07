package com.fincity.saas.ui.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.ui.document.Page;
import com.fincity.saas.ui.model.ComponentDefinition;
import com.fincity.saas.ui.service.PageService;

import reactor.core.publisher.Mono;

/**
 * The two defects in the component-level PATCH routes, both of which only bite
 * on DERIVED documents.
 *
 * That is why they survived: nearly all PATCH traffic runs on root documents,
 * where a delta and a merge are the same thing and the base client is the caller.
 * Cross-client editing makes every edited document derived, so both become the
 * normal case rather than the corner.
 *
 * 1. Authorization was read(pageId) and nothing else. That is the READ check, and
 * an override chain grants a derived client read on every ancestor's document, so
 * any client in the chain could PATCH a component straight into the base's live
 * page with no write access anywhere.
 *
 * 2. read() returns the MERGED page and repo.save(existing) wrote that back over
 * the stored row, flattening the delta: the row stopped being an override, froze
 * a copy of whatever the base held at that moment, and silently stopped
 * inheriting. Nothing surfaced it, and the page looked correct immediately
 * afterwards -- it only diverges the next time the base changes.
 */
@DisplayName("Page component PATCH authorization and delta preservation")
class PageComponentPatchAuthorizationIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "LZBASE";
    private static final String DERIVED = "LZDERIV";

    private static final String PAGE_NAME = "testPage";
    private static final String COMP_KEY = "btnSubmit";

    @Autowired
    private PageService pageService;

    private Page storedPage(String clientCode, String baseClientCode, Map<String, ComponentDefinition> components) {
        Page page = new Page();
        page.setName(PAGE_NAME)
                .setAppCode(APP_CODE)
                .setClientCode(clientCode)
                .setBaseClientCode(baseClientCode)
                .setVersion(1);
        page.setComponentDefinition(components);
        page.setRootComponent("rootComp");
        return this.insertRaw(page);
    }

    private static Map<String, ComponentDefinition> oneComponent(String label) {
        ComponentDefinition cd = new ComponentDefinition();
        cd.setKey(COMP_KEY);
        cd.setName(COMP_KEY);
        cd.setType("Button");
        Map<String, Object> p = new HashMap<>();
        p.put("label", label);
        cd.setProperties(p);
        Map<String, ComponentDefinition> map = new HashMap<>();
        map.put(COMP_KEY, cd);
        return map;
    }

    private static Map<String, Object> patchLabel(String label) {
        Map<String, Object> props = new HashMap<>();
        props.put("label", label);
        Map<String, Object> data = new HashMap<>();
        data.put("properties", props);
        return data;
    }

    private <T> T asClient(Mono<T> mono, String clientCode) {
        ContextAuthentication ca = this.authFor(clientCode, allAuthoritiesFor("Page"));
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca)).block();
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

    @Nested
    @DisplayName("a derived client patching its base's page")
    class Authorization {

        @Test
        @Timeout(30)
        @DisplayName("is refused")
        void isRefused() {

            isolateTenants();
            setInheritance(List.of(BASE, DERIVED));
            Page basePage = storedPage(BASE, null, oneComponent("Base label"));

            assertTrue(refused(() -> asClient(
                    pageService.patchComponent(basePage.getId(), COMP_KEY, patchLabel("hijacked"), 0, "m"),
                    DERIVED)),
                    "a read-level check gated a write: any client in the chain could patch the base's page");
        }

        @Test
        @Timeout(30)
        @DisplayName("leaves the base's page untouched")
        void baseUntouched() {

            isolateTenants();
            setInheritance(List.of(BASE, DERIVED));
            Page basePage = storedPage(BASE, null, oneComponent("Base label"));

            refused(() -> asClient(
                    pageService.patchComponent(basePage.getId(), COMP_KEY, patchLabel("hijacked"), 0, "m"),
                    DERIVED));

            Page stored = mongoTemplate.findById(basePage.getId(), Page.class).block();
            assertNotNull(stored);
            assertEquals("Base label", stored.getComponentDefinition().get(COMP_KEY).getProperties().get("label"),
                    "a refusal that happens after the write is not a refusal");
        }
    }

    @Nested
    @DisplayName("patching a derived page")
    class DeltaPreservation {

        /**
         * The derived row is an empty delta: it inherits the component entirely. A
         * patch must still find the component (so it has to work off the MERGED page)
         * and must still store only the difference.
         */
        @Test
        @Timeout(30)
        @DisplayName("finds a component that exists only on the base")
        void findsInheritedComponent() {

            setInheritance(List.of(BASE, DERIVED));
            storedPage(BASE, null, oneComponent("Base label"));
            Page derivedPage = storedPage(DERIVED, BASE, null);

            Page patched = asClient(
                    pageService.patchComponent(derivedPage.getId(), COMP_KEY, patchLabel("Derived label"), 0, "m"),
                    DERIVED);

            assertNotNull(patched, "patching an inherited component 404d instead of resolving through the chain");
        }

        @Test
        @Timeout(30)
        @DisplayName("keeps the derived row a delta rather than flattening it")
        void doesNotFlattenTheDelta() {

            setInheritance(List.of(BASE, DERIVED));
            Page basePage = storedPage(BASE, null, oneComponent("Base label"));
            Page derivedPage = storedPage(DERIVED, BASE, null);

            asClient(pageService.patchComponent(derivedPage.getId(), COMP_KEY, patchLabel("Derived label"), 0, "m"),
                    DERIVED);

            Page storedDerived = mongoTemplate.findById(derivedPage.getId(), Page.class).block();
            assertNotNull(storedDerived);
            assertEquals(BASE, storedDerived.getBaseClientCode(), "the derived row lost its base");
            // rootComponent is identical on both, so a real delta nulls it out. A
            // flattened row carries the merged value.
            assertNull(storedDerived.getRootComponent(),
                    "the merged document was written back over the delta, so the row stopped being an override");

            // And the base is untouched.
            Page storedBase = mongoTemplate.findById(basePage.getId(), Page.class).block();
            assertNotNull(storedBase);
            assertEquals("Base label", storedBase.getComponentDefinition().get(COMP_KEY).getProperties().get("label"),
                    "patching the derived page wrote into the base");
        }

        @Test
        @Timeout(30)
        @DisplayName("still keeps inheriting the base's later changes")
        void stillInherits() {

            setInheritance(List.of(BASE, DERIVED));
            Page basePage = storedPage(BASE, null, oneComponent("Base label"));
            Page derivedPage = storedPage(DERIVED, BASE, null);

            asClient(pageService.patchComponent(derivedPage.getId(), COMP_KEY, patchLabel("Derived label"), 0, "m"),
                    DERIVED);

            // The base changes something the derived client never touched.
            Page base = mongoTemplate.findById(basePage.getId(), Page.class).block();
            assertNotNull(base);
            base.setRootComponent("newRootFromBase");
            mongoTemplate.save(base).block();

            Page merged = asClient(pageService.read(derivedPage.getId()), DERIVED);
            assertNotNull(merged);
            assertEquals("newRootFromBase", merged.getRootComponent(),
                    "the derived row froze a snapshot of the base and stopped inheriting");
            assertEquals("Derived label", merged.getComponentDefinition().get(COMP_KEY).getProperties().get("label"),
                    "the derived client's own edit was lost");
        }
    }
}
