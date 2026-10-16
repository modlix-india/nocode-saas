package com.fincity.saas.ui.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.fincity.saas.ui.model.ComponentDefinition;
import com.fincity.saas.ui.service.PageService;

import reactor.core.publisher.Mono;

/**
 * Component-level page changes made FOR a client.
 *
 * The id names the page, the target client names whose copy changes. Before, the id
 * was also the copy: a change for a client that had never customized the page landed
 * in the owner's page and reached every client. Hierarchy used: SYSTEM owns the app,
 * MID is managed by SYSTEM, LEAF by MID.
 */
@DisplayName("Page component changes for a target client")
class PageClientPatchIntegrationTest extends AbstractIntegrationTest {

    private static final String MID = "LZMID";
    private static final String LEAF = "LZLEAF";
    private static final String PAGE_NAME = "clientPatchPage";

    @Autowired
    private PageService pageService;

    private static ComponentDefinition component(String key, String type, Map<String, Object> props,
            Map<String, Boolean> children) {
        ComponentDefinition cd = new ComponentDefinition();
        cd.setKey(key);
        cd.setName(key);
        cd.setType(type);
        cd.setProperties(props == null ? null : new HashMap<>(props));
        cd.setChildren(children == null ? null : new HashMap<>(children));
        return cd;
    }

    /** SYSTEM's page: a root grid holding a text and a button. */
    private Page systemPage() {
        Map<String, ComponentDefinition> comps = new HashMap<>();
        comps.put("root", component("root", "Grid", null, Map.of("txt", true, "btn", true)));
        comps.put("txt", component("txt", "Text", Map.of("text", "Hello"), null));
        comps.put("btn", component("btn", "Button", Map.of("label", "Submit"), null));

        Page page = new Page();
        page.setName(PAGE_NAME).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        page.setComponentDefinition(comps);
        page.setRootComponent("root");
        return this.insertRaw(page);
    }

    private static Map<String, Object> label(String label) {
        return Map.of("properties", Map.of("label", label));
    }

    private <T> T as(String clientCode, Mono<T> mono) {
        ContextAuthentication ca = this.authFor(clientCode, allAuthoritiesFor("Page"));
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca)).block();
    }

    private void hierarchy() {
        setInheritance(List.of(SYSTEM, MID, LEAF));
        Mockito.when(this.feignAuthenticationService.getClientHierarchyCodes(MID))
                .thenReturn(Mono.just(List.of(MID, SYSTEM)));
        Mockito.when(this.feignAuthenticationService.getClientHierarchyCodes(LEAF))
                .thenReturn(Mono.just(List.of(LEAF, MID, SYSTEM)));
    }

    private List<Page> stored(String clientCode) {
        return this.findAllRaw(Page.class)
                .filter(p -> PAGE_NAME.equals(p.getName()) && clientCode.equals(p.getClientCode()))
                .collectList()
                .block();
    }

    private Object labelOf(Page page) {
        return page.getComponentDefinition().get("btn").getProperties().get("label");
    }

    @Test
    @Timeout(30)
    @DisplayName("no target: today's behaviour, the page with that id changes")
    void noTarget() {
        hierarchy();
        Page base = systemPage();

        as(SYSTEM, pageService.patchComponent(base.getId(), "btn", label("Go"), 0, "m"));

        assertEquals("Go", labelOf(stored(SYSTEM).get(0)));
        assertTrue(stored(MID).isEmpty());
    }

    @Test
    @Timeout(30)
    @DisplayName("a target with no copy gets one on top of its base, holding only the change")
    void createsTargetCopy() {
        hierarchy();
        Page base = systemPage();

        Page result = as(SYSTEM, pageService.patchComponent(base.getId(), "btn", label("Go MID"), 0, "m",
                MID, false, false));

        assertEquals("Go MID", labelOf(result));
        assertEquals("Submit", labelOf(stored(SYSTEM).get(0)), "the owner's page is untouched");

        Page mid = stored(MID).get(0);
        assertEquals(SYSTEM, mid.getBaseClientCode());
        assertEquals(java.util.Set.of("btn"), mid.getComponentDefinition().keySet(),
                "only the changed component is stored");
    }

    @Test
    @Timeout(30)
    @DisplayName("a target with its own copy has that copy changed, no second copy")
    void updatesOwnCopy() {
        hierarchy();
        Page base = systemPage();

        as(SYSTEM, pageService.patchComponent(base.getId(), "btn", label("one"), 0, "m", MID, false, false));
        Page again = as(SYSTEM, pageService.patchComponent(base.getId(), "txt",
                Map.of("properties", Map.of("text", "Hi MID")), 0, "m", MID, false, false));

        assertEquals(1, stored(MID).size());
        assertEquals("one", labelOf(again));
        assertEquals("Hi MID", again.getComponentDefinition().get("txt").getProperties().get("text"));
    }

    @Test
    @Timeout(30)
    @DisplayName("a base level with no copy: stored as a difference and follows later changes")
    void missingBaseLevel() {
        hierarchy();
        Page base = systemPage();

        as(SYSTEM, pageService.patchComponent(base.getId(), "btn", label("Go LEAF"), 0, "m", LEAF, false, false));

        Page leaf = stored(LEAF).get(0);
        assertEquals(MID, leaf.getBaseClientCode());
        assertEquals(java.util.Set.of("btn"), leaf.getComponentDefinition().keySet());

        as(SYSTEM, pageService.patchComponent(base.getId(), "txt", Map.of("properties", Map.of("text", "Hello 2")),
                0, "m"));

        Page merged = as(LEAF, pageService.readInternal(leaf.getId()));
        assertEquals("Hello 2", merged.getComponentDefinition().get("txt").getProperties().get("text"));
        assertEquals("Go LEAF", labelOf(merged));
    }

    @Test
    @Timeout(30)
    @DisplayName("standalone: the target's copy has no base and holds the whole page")
    void standalone() {
        hierarchy();
        Page base = systemPage();

        as(SYSTEM, pageService.patchComponent(base.getId(), "btn", label("Own"), 0, "m", MID, true, false));

        Page mid = stored(MID).get(0);
        assertNull(mid.getBaseClientCode());
        assertEquals(java.util.Set.of("root", "txt", "btn"), mid.getComponentDefinition().keySet());
    }

    @Test
    @Timeout(30)
    @DisplayName("remove for a target: gone from the target's page only")
    void removeForTarget() {
        hierarchy();
        Page base = systemPage();

        Page result = as(SYSTEM, pageService.removeComponent(base.getId(), "txt", "m", MID, false, false));

        assertFalse(result.getComponentDefinition().containsKey("txt"));
        assertFalse(result.getComponentDefinition().get("root").getChildren().containsKey("txt"));
        Page sys = stored(SYSTEM).get(0);
        assertTrue(sys.getComponentDefinition().containsKey("txt"), "the owner keeps it");
        assertTrue(sys.getComponentDefinition().get("root").getChildren().containsKey("txt"));
    }

    @Test
    @Timeout(30)
    @DisplayName("add for a target: present on the target's page only")
    void addForTarget() {
        hierarchy();
        Page base = systemPage();

        Page result = as(SYSTEM, pageService.addComponent(base.getId(), "extra",
                Map.of("type", "Text", "name", "extra", "properties", Map.of("text", "only MID")), "root", "m", MID,
                false, false));

        assertNotNull(result.getComponentDefinition().get("extra"));
        assertTrue(result.getComponentDefinition().get("root").getChildren().containsKey("extra"));
        assertFalse(stored(SYSTEM).get(0).getComponentDefinition().containsKey("extra"));
    }

    @Test
    @Timeout(30)
    @DisplayName("an event function for a target is stored in the target's copy")
    void eventForTarget() {
        hierarchy();
        Page base = systemPage();

        Page result = as(SYSTEM, pageService.patchEventFunction(base.getId(), "onSubmit",
                Map.of("name", "onSubmit", "steps", Map.of()), 0, "m", MID, false, false));

        assertNotNull(result.getEventFunctions().get("onSubmit"));
        Page sys = stored(SYSTEM).get(0);
        assertTrue(sys.getEventFunctions() == null || !sys.getEventFunctions().containsKey("onSubmit"));
        assertNotNull(stored(MID).get(0).getEventFunctions().get("onSubmit"));
    }

    @Test
    @Timeout(30)
    @DisplayName("a caller that does not manage the target is refused and nothing is written")
    void unrelatedCallerRefused() {
        hierarchy();
        Page base = systemPage();
        Mockito.when(this.feignAuthenticationService.doesClientManageClientCode(Mockito.anyString(),
                Mockito.anyString())).thenReturn(Mono.just(Boolean.FALSE));

        boolean refused;
        try {
            as("OTHER", pageService.patchComponent(base.getId(), "btn", label("x"), 0, "m", LEAF, false, false));
            refused = false;
        } catch (Exception e) {
            refused = true;
        }

        assertTrue(refused);
        assertTrue(stored(LEAF).isEmpty());
        assertEquals("Submit", labelOf(stored(SYSTEM).get(0)));
    }

    @Test
    @Timeout(30)
    @DisplayName("draft for a target with no copy: an unpublished copy; draft again: the copy's draft")
    void draftForTarget() {
        hierarchy();
        Page base = systemPage();

        Page first = as(SYSTEM, pageService.patchComponent(base.getId(), "btn", label("Draft MID"), 0, "m", MID,
                false, true));
        assertEquals("Draft MID", labelOf(first));

        Page mid = stored(MID).get(0);
        assertEquals(Boolean.FALSE, mid.getPublished(), "never published: hidden from the live surface");
        assertEquals(SYSTEM, mid.getBaseClientCode());
        assertEquals("Submit", labelOf(stored(SYSTEM).get(0)));

        Page second = as(SYSTEM, pageService.patchComponent(base.getId(), "txt",
                Map.of("properties", Map.of("text", "Draft text")), 0, "m", MID, false, true));
        assertEquals("Draft text", second.getComponentDefinition().get("txt").getProperties().get("text"));
        assertEquals(1, stored(MID).size());

        // The unpublished copy is real unpublished work: it publishes like any draft.
        as(SYSTEM, pageService.publish(mid.getId(), "publish MID"));
        Page live = stored(MID).get(0);
        assertEquals(Boolean.TRUE, live.getPublished());
        Page merged = as(MID, pageService.readInternal(live.getId()));
        assertEquals("Draft MID", labelOf(merged));
        assertEquals("Draft text", merged.getComponentDefinition().get("txt").getProperties().get("text"));
    }

    @Test
    @Timeout(30)
    @DisplayName("a client with no copy, on its own URL, is served its managing client's customization")
    void readFollowsManagingChain() {
        hierarchy();
        Page base = systemPage();
        as(SYSTEM, pageService.patchComponent(base.getId(), "btn", label("MID label"), 0, "m", MID, false, false));

        // LEAF on its own URL: app inheritance alone is [SYSTEM, LEAF], which skipped MID.
        setInheritance(List.of(SYSTEM, LEAF));
        Page leafView = as(LEAF, pageService.read(PAGE_NAME, APP_CODE, LEAF)).getObject();

        assertEquals("MID label", labelOf(leafView));
    }

    private long draftRows() {
        return this.mongoTemplate.findAll(org.bson.Document.class, "draft")
                .filter(d -> PAGE_NAME.equals(d.get("objectName")))
                .count()
                .block();
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
    @DisplayName("a caller that cannot READ the page named by the id is refused before anything is looked up")
    void unreadablePageIdRefused() {
        hierarchy();
        Page base = systemPage();
        Mockito.when(this.feignAuthenticationService.doesClientManageClientCode(Mockito.anyString(),
                Mockito.anyString())).thenReturn(Mono.just(Boolean.FALSE));
        Mockito.when(this.feignAuthenticationService.hasReadAccess(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Mono.just(Boolean.FALSE));

        assertTrue(refused(() -> as("OTHER", pageService.patchComponent(base.getId(), "btn", label("x"), 0, "m",
                "OTHER", false, false))));
        assertTrue(stored("OTHER").isEmpty(), "nothing created for the caller");
        assertEquals("Submit", labelOf(stored(SYSTEM).get(0)));
    }

    @Test
    @Timeout(30)
    @DisplayName("draft on another client's copy without UPDATE on it: refused, no fork into the caller, no draft row")
    void draftWithoutUpdateRefusedWithoutFork() {
        hierarchy();
        Page base = systemPage();
        as(SYSTEM, pageService.patchComponent(base.getId(), "btn", label("MID own"), 0, "m", MID, false, false));

        // LEAF can read the page (it is in the chain) but does not manage MID.
        Mockito.when(this.feignAuthenticationService.doesClientManageClientCode(LEAF, MID))
                .thenReturn(Mono.just(Boolean.FALSE));
        long drafts = draftRows();

        assertTrue(refused(() -> as(LEAF, pageService.patchComponent(base.getId(), "btn", label("leaf draft"), 0,
                "m", MID, false, true))));
        assertTrue(stored(LEAF).isEmpty(), "saveDraft's fork-for-caller must not run");
        assertEquals(drafts, draftRows(), "no draft row written");
        assertEquals("MID own", labelOf(stored(MID).get(0)));
    }

    @Test
    @Timeout(30)
    @DisplayName("no write access to the app: every write path is refused (create, own copy live, own copy draft)")
    void noAppWriteAccessRefused() {
        hierarchy();
        Page base = systemPage();
        as(SYSTEM, pageService.patchComponent(base.getId(), "btn", label("MID own"), 0, "m", MID, false, false));

        // Every Page authority and it manages both targets; only the app write access is gone.
        Mockito.when(this.feignAuthenticationService.hasWriteAccess(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Mono.just(Boolean.FALSE));

        assertTrue(refused(() -> as(SYSTEM, pageService.patchComponent(base.getId(), "btn", label("x"), 0, "m",
                LEAF, false, false))), "create of LEAF's copy");
        assertTrue(refused(() -> as(SYSTEM, pageService.patchComponent(base.getId(), "btn", label("x"), 0, "m",
                MID, false, false))), "live change to MID's own copy");
        assertTrue(refused(() -> as(SYSTEM, pageService.patchComponent(base.getId(), "btn", label("x"), 0, "m",
                MID, false, true))), "draft change to MID's own copy");

        assertTrue(stored(LEAF).isEmpty());
        assertEquals("MID own", labelOf(stored(MID).get(0)));
    }
}
