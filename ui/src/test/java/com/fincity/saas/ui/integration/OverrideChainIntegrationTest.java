package com.fincity.saas.ui.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.ui.document.Page;
import com.fincity.saas.ui.service.PageService;

import reactor.core.publisher.Mono;

/**
 * The override chain, exercised through the real service over real Mongo.
 *
 * `getMergedSources` folds the `baseClientCode` chain to produce "everything
 * below me, merged, excluding me". `expandDeep` yields [self, mid, root], and
 * the fold is expected to return mid-over-root.
 *
 * Depth 2 is the only depth any existing code path is known to handle. Depth 3
 * is where the fold seeds at the wrong element, and depth 3 is reachable through
 * two successive createForClient calls. These tests pin the contract at every
 * depth so the fix can be verified and cannot regress.
 */
@DisplayName("Override chain across client hierarchy depths")
class OverrideChainIntegrationTest extends AbstractIntegrationTest {

    private static final String MID = "LZCLA";
    private static final String LEAF = "LZACP1";
    private static final String PAGE_NAME = "testPage";

    @Autowired
    private PageService pageService;

    // ---------------------------------------------------------------- fixtures

    /**
     * Written straight to Mongo rather than through create(), because a fixture
     * has to be the stored (delta) form. Going through the service would re-run
     * extractOverride against the very chain under test.
     */
    private Page storedPage(String clientCode, String baseClientCode, Map<String, Object> properties,
            String rootComponent) {

        Page page = new Page();
        page.setName(PAGE_NAME)
                .setAppCode(APP_CODE)
                .setClientCode(clientCode)
                .setBaseClientCode(baseClientCode)
                .setVersion(1);
        page.setProperties(properties == null ? null : new HashMap<>(properties));
        page.setRootComponent(rootComponent);
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

    // ------------------------------------------------------------------- depth 1

    @Nested
    @DisplayName("CH1: a single base client")
    class SingleClient {

        @Test
        @DisplayName("reads its own content unchanged")
        void readsOwnContent() {

            setInheritance(List.of(SYSTEM));
            Page root = storedPage(SYSTEM, null, props("fromRoot", "r", "shared", "root"), "rootComp");

            Page read = asClient(pageService.read(root.getId()), SYSTEM);

            assertNotNull(read);
            assertEquals("r", read.getProperties().get("fromRoot"));
            assertEquals("root", read.getProperties().get("shared"));
            assertEquals("rootComp", read.getRootComponent());
        }
    }

    // ------------------------------------------------------------------- depth 2

    @Nested
    @DisplayName("CH2: base -> child")
    class TwoDeep {

        @Test
        @DisplayName("read merges the child over the base")
        void readMergesChildOverBase() {

            setInheritance(List.of(SYSTEM, MID));
            storedPage(SYSTEM, null, props("fromRoot", "r", "shared", "root"), "rootComp");
            Page mid = storedPage(MID, SYSTEM, props("fromMid", "m", "shared", "mid"), null);

            Page read = asClient(pageService.read(mid.getId()), MID);

            assertNotNull(read);
            assertEquals("r", read.getProperties().get("fromRoot"), "base-only key must survive the merge");
            assertEquals("m", read.getProperties().get("fromMid"));
            assertEquals("mid", read.getProperties().get("shared"), "the child must win on a shared key");
        }

        @Test
        @DisplayName("update keeps the child's own delta")
        void updateKeepsChildDelta() {

            setInheritance(List.of(SYSTEM, MID));
            storedPage(SYSTEM, null, props("fromRoot", "r", "shared", "root"), "rootComp");
            Page mid = storedPage(MID, SYSTEM, props("fromMid", "m", "shared", "mid"), null);

            Page toSave = asClient(pageService.read(mid.getId()), MID);
            toSave.getProperties().put("fromMid", "m2");

            asClient(pageService.update(toSave), MID);

            Page stored = mongoTemplate.findById(mid.getId(), Page.class).block();
            assertNotNull(stored);
            assertNotNull(stored.getProperties(), "the stored override must not be emptied");
            assertEquals("m2", stored.getProperties().get("fromMid"));
        }
    }

    // ------------------------------------------------------------------- depth 3

    @Nested
    @DisplayName("CH3: base -> mid -> leaf")
    class ThreeDeep {

        @Test
        @DisplayName("read merges all three levels, with the most derived winning")
        void readMergesAllThreeLevels() {

            setInheritance(List.of(SYSTEM, MID, LEAF));
            storedPage(SYSTEM, null, props("fromRoot", "r", "sharedRootMid", "root", "sharedAll", "root"), "rootComp");
            storedPage(MID, SYSTEM, props("fromMid", "m", "sharedRootMid", "mid", "sharedAll", "mid"), null);
            Page leaf = storedPage(LEAF, MID, props("fromLeaf", "l", "sharedAll", "leaf"), null);

            Page read = asClient(pageService.read(leaf.getId()), LEAF);

            assertNotNull(read);
            Map<String, Object> p = read.getProperties();

            // The case that matters: a key defined only at the root, two levels up.
            assertEquals("r", p.get("fromRoot"), "a root-only key must survive a three-level merge");

            assertEquals("m", p.get("fromMid"));
            assertEquals("l", p.get("fromLeaf"));
            assertEquals("mid", p.get("sharedRootMid"), "mid must win over root");
            assertEquals("leaf", p.get("sharedAll"), "leaf must win over both");
            assertEquals("rootComp", read.getRootComponent(), "rootComponent must inherit from the root");
        }

        @Test
        @DisplayName("update keeps the leaf's own delta and does not empty it")
        void updateKeepsLeafDelta() {

            setInheritance(List.of(SYSTEM, MID, LEAF));
            storedPage(SYSTEM, null, props("fromRoot", "r", "sharedAll", "root"), "rootComp");
            storedPage(MID, SYSTEM, props("fromMid", "m", "sharedAll", "mid"), null);
            Page leaf = storedPage(LEAF, MID, props("fromLeaf", "l", "sharedAll", "leaf"), null);

            Page toSave = asClient(pageService.read(leaf.getId()), LEAF);
            toSave.getProperties().put("fromLeaf", "l2");

            asClient(pageService.update(toSave), LEAF);

            Page stored = mongoTemplate.findById(leaf.getId(), Page.class).block();
            assertNotNull(stored);
            assertNotNull(stored.getProperties(),
                    "a three-deep override must not be saved as an empty document");
            assertEquals("l2", stored.getProperties().get("fromLeaf"));
        }

        @Test
        @DisplayName("update then read round-trips")
        void updateThenReadRoundTrips() {

            setInheritance(List.of(SYSTEM, MID, LEAF));
            storedPage(SYSTEM, null, props("fromRoot", "r"), "rootComp");
            storedPage(MID, SYSTEM, props("fromMid", "m"), null);
            Page leaf = storedPage(LEAF, MID, props("fromLeaf", "l"), null);

            Page toSave = asClient(pageService.read(leaf.getId()), LEAF);
            toSave.getProperties().put("fromLeaf", "changed");
            asClient(pageService.update(toSave), LEAF);

            Page reread = asClient(pageService.read(leaf.getId()), LEAF);

            assertNotNull(reread);
            assertEquals("changed", reread.getProperties().get("fromLeaf"));
            assertEquals("r", reread.getProperties().get("fromRoot"), "the root's content must still be merged in");
            assertEquals("m", reread.getProperties().get("fromMid"));
        }

        @Test
        @DisplayName("reading does not mutate the stored leaf document")
        void readDoesNotMutateStoredLeaf() {

            setInheritance(List.of(SYSTEM, MID, LEAF));
            storedPage(SYSTEM, null, props("fromRoot", "r"), "rootComp");
            storedPage(MID, SYSTEM, props("fromMid", "m"), null);
            Page leaf = storedPage(LEAF, MID, props("fromLeaf", "l"), null);

            Page read = asClient(pageService.read(leaf.getId()), LEAF);
            assertNotNull(read);

            Page stored = mongoTemplate.findById(leaf.getId(), Page.class).block();
            assertNotNull(stored);
            assertNotSame(read, stored);

            // A read must not have written the merged content back onto the override.
            assertEquals(1, stored.getProperties().size(),
                    "the stored leaf must still hold only its own delta after a read");
            assertEquals("l", stored.getProperties().get("fromLeaf"));
        }
    }

    // ------------------------------------------------------------------- depth 4

    @Nested
    @DisplayName("CH4: base -> mid -> leaf -> leaf2")
    class FourDeep {

        private static final String LEAF2 = "LZACP1X";

        @Test
        @DisplayName("read merges all four levels")
        void readMergesAllFourLevels() {

            setInheritance(List.of(SYSTEM, MID, LEAF, LEAF2));
            storedPage(SYSTEM, null, props("fromRoot", "r", "sharedAll", "root"), "rootComp");
            storedPage(MID, SYSTEM, props("fromMid", "m", "sharedAll", "mid"), null);
            storedPage(LEAF, MID, props("fromLeaf", "l", "sharedAll", "leaf"), null);
            Page leaf2 = storedPage(LEAF2, LEAF, props("fromLeaf2", "l2", "sharedAll", "leaf2"), null);

            Page read = asClient(pageService.read(leaf2.getId()), LEAF2);

            assertNotNull(read);
            Map<String, Object> p = read.getProperties();
            assertEquals("r", p.get("fromRoot"), "a root-only key must survive a four-level merge");
            assertEquals("m", p.get("fromMid"));
            assertEquals("l", p.get("fromLeaf"));
            assertEquals("l2", p.get("fromLeaf2"));
            assertEquals("leaf2", p.get("sharedAll"), "the most derived client must win");
        }
    }
}
