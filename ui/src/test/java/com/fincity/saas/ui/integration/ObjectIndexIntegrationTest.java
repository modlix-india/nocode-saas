package com.fincity.saas.ui.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.ui.document.Page;
import com.fincity.saas.ui.service.UIIndexService;

import reactor.core.publisher.Mono;

/**
 * The object index, which backs the builder's object tree.
 *
 * The tree showed the same page two and three times for any app installed for
 * more than one client: the index queried Mongo on `appCode` alone, so every
 * client's copy of a name came back as its own row, and rows belonging to
 * clients outside the caller's inheritance chain came with them. These pin the
 * index to the same resolution the per-type list routes perform.
 */
@DisplayName("Object index resolves the override chain")
class ObjectIndexIntegrationTest extends AbstractIntegrationTest {

    private static final String MID = "LZCLA";
    private static final String OUTSIDER = "OTHERCL";

    @Autowired
    private UIIndexService indexService;

    private Page storedPage(String name, String clientCode, String baseClientCode) {

        Page page = new Page();
        page.setName(name)
                .setAppCode(APP_CODE)
                .setClientCode(clientCode)
                .setBaseClientCode(baseClientCode)
                .setVersion(1);
        return this.insertRaw(page);
    }

    private <T> T asClient(Mono<T> mono, String clientCode) {
        ContextAuthentication ca = this.authFor(clientCode, allAuthoritiesFor("Page"));
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca)).block();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> pagesOf(Map<String, Object> index) {
        return (List<Map<String, Object>>) index.get("pages");
    }

    private Map<String, Object> rowNamed(List<Map<String, Object>> rows, String name) {
        return rows.stream().filter(r -> name.equals(r.get("name"))).findFirst().orElse(null);
    }

    private long countNamed(List<Map<String, Object>> rows, String name) {
        return rows.stream().filter(r -> name.equals(r.get("name"))).count();
    }

    @Test
    @DisplayName("a name overridden by the client appears once, as the client's copy")
    void overriddenNameAppearsOnce() {

        setInheritance(List.of(SYSTEM, MID));
        storedPage("sharedPage", SYSTEM, null);
        Page override = storedPage("sharedPage", MID, SYSTEM);
        storedPage("baseOnlyPage", SYSTEM, null);

        Map<String, Object> index = asClient(indexService.buildIndex(APP_CODE, null, false), MID);

        assertNotNull(index);
        List<Map<String, Object>> pages = pagesOf(index);

        assertEquals(1, countNamed(pages, "sharedPage"),
                "the base and the override are one object, so they are one row");
        assertEquals(2, pages.size(), "the override must not add a row of its own");

        Map<String, Object> shared = rowNamed(pages, "sharedPage");
        assertNotNull(shared);
        assertEquals(MID, shared.get("clientCode"), "the client's own copy must win the chain");
        assertEquals(override.getId(), shared.get("id"), "and it must be the id the editor opens");

        assertNotNull(rowNamed(pages, "baseOnlyPage"), "a name only the base has must still be listed");
    }

    @Test
    @DisplayName("the base copy is what a client without an override sees")
    void baseCopyServesAClientWithoutAnOverride() {

        setInheritance(List.of(SYSTEM, MID));
        Page base = storedPage("sharedPage", SYSTEM, null);

        Map<String, Object> index = asClient(indexService.buildIndex(APP_CODE, null, false), MID);

        assertNotNull(index);
        List<Map<String, Object>> pages = pagesOf(index);

        assertEquals(1, pages.size());
        assertEquals(base.getId(), pages.get(0).get("id"));
        assertEquals(SYSTEM, pages.get(0).get("clientCode"));
    }

    @Test
    @DisplayName("a client outside the inheritance chain contributes nothing")
    void objectsOutsideTheChainAreExcluded() {

        setInheritance(List.of(SYSTEM, MID));
        storedPage("sharedPage", SYSTEM, null);
        storedPage("foreignPage", OUTSIDER, SYSTEM);

        Map<String, Object> index = asClient(indexService.buildIndex(APP_CODE, null, false), MID);

        assertNotNull(index);
        List<Map<String, Object>> pages = pagesOf(index);

        assertEquals(1, pages.size(), "only the chain's objects belong in the tree");
        assertNotNull(rowNamed(pages, "sharedPage"));
        assertEquals(0, countNamed(pages, "foreignPage"),
                "another client's page must not leak its name or its id");
    }

    @Test
    @DisplayName("a different app's objects are never in the index")
    void otherAppsAreExcluded() {

        setInheritance(List.of(SYSTEM));
        storedPage("minePage", SYSTEM, null);

        Page other = new Page();
        other.setName("theirsPage")
                .setAppCode("otherapp")
                .setClientCode(SYSTEM)
                .setVersion(1);
        insertRaw(other);

        Map<String, Object> index = asClient(indexService.buildIndex(APP_CODE, null, false), SYSTEM);

        assertNotNull(index);
        List<Map<String, Object>> pages = pagesOf(index);

        assertEquals(1, pages.size());
        assertEquals("minePage", pages.get(0).get("name"));
    }

    @Test
    @DisplayName("the response names the client it indexed, even when none was asked for")
    void reportsTheResolvedClient() {

        setInheritance(List.of(SYSTEM, MID));
        storedPage("sharedPage", SYSTEM, null);

        Map<String, Object> index = asClient(indexService.buildIndex(APP_CODE, null, false), MID);

        assertNotNull(index);
        assertEquals(APP_CODE, index.get("appCode"));
        assertEquals(MID, index.get("clientCode"),
                "a caller that omitted the client must still learn whose view this is");
    }

    @Test
    @DisplayName("every object type is keyed, empty or not")
    void everyTypeIsPresent() {

        setInheritance(List.of(SYSTEM));
        storedPage("minePage", SYSTEM, null);

        Map<String, Object> index = asClient(indexService.buildIndex(APP_CODE, null, false), SYSTEM);

        assertNotNull(index);
        for (String key : List.of("applications", "pages", "functions", "schemas", "themes", "styles", "uripaths"))
            assertTrue(index.containsKey(key), key + " must be keyed so a tree can render an empty group");
    }
}
