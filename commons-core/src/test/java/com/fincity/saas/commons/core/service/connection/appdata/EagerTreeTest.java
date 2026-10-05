package com.fincity.saas.commons.core.service.connection.appdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * eagerFields are PATHS now, so "category.parent" expands two levels.
 *
 * Paths rather than a new request shape because every caller and every KIRun
 * signature already passes a flat list of strings; a dot extends that without
 * changing any of them. Which makes the parser the whole contract.
 */
class EagerTreeTest {

    @Test
    @DisplayName("A plain field is one level, as it always was")
    void flatFieldIsUnchanged() {

        Map<String, Map<String, Object>> tree = AppDataService.eagerTree(List.of("category"));

        assertEquals(1, tree.size());
        assertTrue(tree.get("category").isEmpty());
    }

    @Test
    @DisplayName("A dotted path nests")
    void dottedPathNests() {

        Map<String, Map<String, Object>> tree = AppDataService.eagerTree(List.of("category.parent"));

        assertEquals(List.of("category"), List.copyOf(tree.keySet()));
        assertEquals(List.of("parent"), List.copyOf(tree.get("category").keySet()));
    }

    /**
     * The point of merging: asking for two things under one relation must expand
     * that relation ONCE, or the saving that makes depth affordable disappears.
     */
    @Test
    @DisplayName("Siblings merge under one parent rather than fetching it twice")
    void siblingsMerge() {

        Map<String, Map<String, Object>> tree =
                AppDataService.eagerTree(List.of("category.parent", "category.owner", "author"));

        assertEquals(2, tree.size());
        assertEquals(2, tree.get("category").size());
        assertTrue(tree.get("category").containsKey("parent"));
        assertTrue(tree.get("category").containsKey("owner"));
        assertTrue(tree.get("author").isEmpty());
    }

    @Test
    @DisplayName("A deeper path keeps nesting")
    void deepPath() {

        Map<String, Map<String, Object>> tree = AppDataService.eagerTree(List.of("a.b.c"));

        @SuppressWarnings("unchecked")
        Map<String, Object> b = (Map<String, Object>) (Map<String, ?>) tree.get("a").get("b");
        assertTrue(b instanceof Map);
        assertTrue(((Map<?, ?>) b).containsKey("c"));
    }

    @Test
    @DisplayName("Null, blank and ragged input produce nothing rather than throwing")
    void toleratesRubbish() {

        assertTrue(AppDataService.eagerTree(null).isEmpty());
        assertTrue(AppDataService.eagerTree(List.of("")).isEmpty());
        assertTrue(AppDataService.eagerTree(List.of("   ")).isEmpty());

        Map<String, Map<String, Object>> trailing = AppDataService.eagerTree(List.of("category."));
        assertEquals(List.of("category"), List.copyOf(trailing.keySet()));
        assertTrue(trailing.get("category").isEmpty());
    }

    @Test
    @DisplayName("Whitespace around a segment is ignored")
    void trimsSegments() {

        Map<String, Map<String, Object>> tree = AppDataService.eagerTree(List.of(" category . parent "));

        assertTrue(tree.containsKey("category"));
        assertTrue(tree.get("category").containsKey("parent"));
    }
}
