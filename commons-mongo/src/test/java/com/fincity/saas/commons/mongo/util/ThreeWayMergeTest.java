package com.fincity.saas.commons.mongo.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The rules a publish depends on, stated as cases.
 *
 * The one that matters most is the first: a draft that never mentioned a key must
 * not carry that key's OLD value forward. That is the whole reason a publish could
 * not simply overwrite the live document and had to fail instead.
 */
@DisplayName("Three-way merge")
class ThreeWayMergeTest {

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2)
            m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static ThreeWayMerge.Result merge(Map<String, Object> base, Map<String, Object> live,
            Map<String, Object> draft) {
        return ThreeWayMerge.merge(base, live, draft, Set.of());
    }

    @Nested
    @DisplayName("at one level")
    class Flat {

        @Test
        @DisplayName("a key only the live document changed keeps the live value")
        void liveOnlyChangeSurvives() {

            ThreeWayMerge.Result r = merge(map("a", 1, "b", 2), map("a", 1, "b", 99), map("a", 7, "b", 2));

            assertEquals(map("a", 7, "b", 99), r.merged());
            assertTrue(r.isClean());
        }

        @Test
        @DisplayName("a key only the draft changed keeps the draft value")
        void draftOnlyChangeSurvives() {

            ThreeWayMerge.Result r = merge(map("a", 1), map("a", 1), map("a", 2));

            assertEquals(map("a", 2), r.merged());
            assertTrue(r.isClean());
        }

        @Test
        @DisplayName("both sides making the same change is not a conflict")
        void identicalChangeIsNotAConflict() {

            ThreeWayMerge.Result r = merge(map("a", 1), map("a", 2), map("a", 2));

            assertEquals(map("a", 2), r.merged());
            assertTrue(r.isClean());
        }

        @Test
        @DisplayName("both sides changing one value to different values reports the path and keeps the draft")
        void realConflictKeepsTheDraft() {

            ThreeWayMerge.Result r = merge(map("a", 1), map("a", 2), map("a", 3));

            assertEquals(map("a", 3), r.merged());
            assertFalse(r.isClean());
            assertEquals(List.of("a"), r.conflicts());
        }

        @Test
        @DisplayName("a key the live document added and the draft never saw is carried in")
        void liveAdditionIsCarriedIn() {

            ThreeWayMerge.Result r = merge(map("a", 1), map("a", 1, "title", "Web Smith"), map("a", 1));

            assertEquals(map("a", 1, "title", "Web Smith"), r.merged());
            assertTrue(r.isClean());
        }

        @Test
        @DisplayName("a key the live document deleted stays deleted when the draft did not touch it")
        void liveDeletionIsHonoured() {

            ThreeWayMerge.Result r = merge(map("a", 1, "gone", 2), map("a", 1), map("a", 1, "gone", 2));

            assertEquals(map("a", 1), r.merged());
            assertTrue(r.isClean());
        }

        @Test
        @DisplayName("a key the draft deleted stays deleted when the live document did not touch it")
        void draftDeletionIsHonoured() {

            ThreeWayMerge.Result r = merge(map("a", 1, "gone", 2), map("a", 1, "gone", 2), map("a", 1));

            assertEquals(map("a", 1), r.merged());
            assertTrue(r.isClean());
        }

        @Test
        @DisplayName("a null value is a value, not an absence")
        void explicitNullIsNotADeletion() {

            Map<String, Object> live = new HashMap<>();
            live.put("a", 1);
            live.put("n", null);

            ThreeWayMerge.Result r = merge(map("a", 1), live, map("a", 1));

            assertTrue(r.merged().containsKey("n"), "the live document's explicit null was dropped as if absent");
            assertTrue(r.isClean());
        }
    }

    @Nested
    @DisplayName("in nested trees")
    class Nested2 {

        @Test
        @DisplayName("disjoint edits in one subtree both survive")
        void disjointSubtreeEditsBothSurvive() {

            ThreeWayMerge.Result r = merge(
                    map("properties", map("theme", "light")),
                    map("properties", map("theme", "light", "title", "Web Smith")),
                    map("properties", map("theme", "light", "fontPacks", map("dm", "DM_SANS"))));

            assertEquals(map("properties", map("theme", "light", "title", "Web Smith",
                    "fontPacks", map("dm", "DM_SANS"))), r.merged());
            assertTrue(r.isClean());
        }

        @Test
        @DisplayName("two independently added subtrees merge into their union rather than one winning")
        void independentAdditionsUnion() {

            ThreeWayMerge.Result r = merge(
                    map("p", map()),
                    map("p", map("links", map("a", 1))),
                    map("p", map("fonts", map("b", 2))));

            assertEquals(map("p", map("links", map("a", 1), "fonts", map("b", 2))), r.merged());
            assertTrue(r.isClean());
        }

        @Test
        @DisplayName("a conflict names its full dotted path")
        void conflictPathIsDotted() {

            ThreeWayMerge.Result r = merge(
                    map("p", map("t", "a")),
                    map("p", map("t", "b")),
                    map("p", map("t", "c")));

            assertEquals(List.of("p.t"), r.conflicts());
            assertEquals(map("p", map("t", "c")), r.merged());
        }

        @Test
        @DisplayName("a conflict in one branch does not disturb a clean merge in another")
        void conflictIsConfined() {

            ThreeWayMerge.Result r = merge(
                    map("p", map("t", "a", "u", 1)),
                    map("p", map("t", "b", "u", 1, "v", 9)),
                    map("p", map("t", "c", "u", 2)));

            assertEquals(map("p", map("t", "c", "u", 2, "v", 9)), r.merged());
            assertEquals(List.of("p.t"), r.conflicts());
        }
    }

    @Test
    @DisplayName("lists are whole values: two sides editing one list is a conflict, not a splice")
    void listsAreAtomic() {

        ThreeWayMerge.Result r = merge(
                map("languages", List.of("en")),
                map("languages", List.of("en", "fr")),
                map("languages", List.of("en", "de")));

        assertEquals(map("languages", List.of("en", "de")), r.merged());
        assertEquals(List.of("languages"), r.conflicts());
    }

    @Test
    @DisplayName("a list only one side changed merges cleanly")
    void oneSidedListChange() {

        ThreeWayMerge.Result r = merge(
                map("languages", List.of("en")),
                map("languages", List.of("en", "fr")),
                map("languages", List.of("en")));

        assertEquals(map("languages", List.of("en", "fr")), r.merged());
        assertTrue(r.isClean());
    }

    @Test
    @DisplayName("ignored keys are dropped at the root only, never deeper")
    void ignoredKeysAreRootOnly() {

        ThreeWayMerge.Result r = ThreeWayMerge.merge(
                map("version", 1, "p", map("version", "1.2.0")),
                map("version", 3, "p", map("version", "1.2.0")),
                map("version", 1, "p", map("version", "1.3.0")),
                Set.of("version"));

        assertFalse(r.merged().containsKey("version"), "the root version was merged into the content");
        assertEquals("1.3.0", ((Map<?, ?>) r.merged().get("p")).get("version"),
                "a nested field that happens to share a name with an ignored root key was dropped");
    }

    @Test
    @DisplayName("a null side is treated as an empty tree rather than throwing")
    void nullsAreTolerated() {

        ThreeWayMerge.Result r = ThreeWayMerge.merge(null, map("a", 1), null, Set.of());

        assertEquals(map("a", 1), r.merged());
        assertTrue(r.isClean());
    }
}
