package com.fincity.saas.commons.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

class TopologicalUtilTest {

    private static final Function<String, String> KEY = Function.identity();

    private static TopologicalUtil.Ordered<String> sort(Map<String, List<String>> graph) {
        Function<String, Collection<String>> deps = k -> graph.getOrDefault(k, List.of());
        return TopologicalUtil.sort(List.copyOf(graph.keySet()), KEY, deps);
    }

    @Test
    void nullAndEmptyGiveNothing() {
        assertEquals(List.of(), TopologicalUtil.sort(null, KEY, k -> List.of()).waves());
        assertEquals(List.of(), TopologicalUtil.sort(List.of(), KEY, k -> List.of()).cyclic());
    }

    @Test
    void independentItemsAllLandInOneWave() {

        var ordered = TopologicalUtil.sort(List.of("a", "b", "c"), KEY, k -> List.of());

        assertEquals(1, ordered.waves().size());
        assertEquals(List.of("a", "b", "c"), ordered.waves().get(0));
        assertTrue(ordered.cyclic().isEmpty());
    }

    @Test
    void aChainBecomesOneWavePerLink() {

        // c -> b -> a
        var ordered = sort(Map.of("a", List.of(), "b", List.of("a"), "c", List.of("b")));

        assertEquals(List.of(List.of("a"), List.of("b"), List.of("c")), ordered.waves());
        assertTrue(ordered.cyclic().isEmpty());
    }

    @Test
    void aDiamondCollapsesTheTwoMiddleNodesIntoOneWave() {

        // d depends on b and c, both of which depend on a
        var ordered = sort(Map.of("a", List.of(), "b", List.of("a"), "c", List.of("a"), "d", List.of("b", "c")));

        assertEquals(3, ordered.waves().size());
        assertEquals(List.of("a"), ordered.waves().get(0));
        assertEquals(2, ordered.waves().get(1).size());
        assertTrue(ordered.waves().get(1).containsAll(List.of("b", "c")));
        assertEquals(List.of("d"), ordered.waves().get(2));
    }

    @Test
    void dependenciesOutsideTheSetAreIgnored() {

        // "b" is not in the set, so nothing orders around it
        var ordered = TopologicalUtil.sort(List.of("a"), KEY, k -> List.of("b"));

        assertEquals(List.of(List.of("a")), ordered.waves());
        assertTrue(ordered.cyclic().isEmpty());
    }

    @Test
    void aSelfDependencyIsAOneNodeCycle() {

        var ordered = TopologicalUtil.sort(List.of("a"), KEY, k -> List.of("a"));

        assertTrue(ordered.waves().isEmpty());
        assertEquals(List.of("a"), ordered.cyclic());
    }

    @Test
    void aTwoNodeCycleComesBackWholeAndInInputOrder() {

        var ordered = sort(Map.of("a", List.of("b"), "b", List.of("a")));

        assertTrue(ordered.waves().isEmpty());
        assertEquals(2, ordered.cyclic().size());
        assertTrue(ordered.cyclic().containsAll(List.of("a", "b")));
    }

    @Test
    void whatWaitsBehindACycleIsCyclicToo() {

        // a <-> b is the cycle, c only depends on a, d depends on nothing
        var ordered = sort(Map.of("a", List.of("b"), "b", List.of("a"), "c", List.of("a"), "d", List.of()));

        assertEquals(List.of(List.of("d")), ordered.waves());
        assertEquals(3, ordered.cyclic().size());
        assertTrue(ordered.cyclic().containsAll(List.of("a", "b", "c")));
    }

    @Test
    void duplicateKeysAreAllKeptAndTreatedAsOneNode() {

        // Two items claiming "a", plus a "b" that waits on "a"
        record Item(String key, List<String> deps) {
        }
        List<Item> items = List.of(
                new Item("a", List.of()), new Item("a", List.of()), new Item("b", List.of("a")));

        var ordered = TopologicalUtil.sort(items, Item::key, Item::deps);

        assertEquals(2, ordered.waves().size());
        assertEquals(2, ordered.waves().get(0).size(), "both items keyed 'a' must survive");
        assertEquals(1, ordered.waves().get(1).size());
        assertTrue(ordered.cyclic().isEmpty());
    }

    @Test
    void itemsWithNoKeyAreKeptRatherThanDropped() {

        var ordered = TopologicalUtil.sort(java.util.Arrays.asList("a", null, "b"),
                k -> k, k -> List.of());

        assertEquals(1, ordered.waves().size());
        assertEquals(3, ordered.waves().get(0).size());
        assertTrue(ordered.waves().get(0).contains(null));
    }

    @Test
    void anUnkeyedItemStillComesOutWhenEverythingElseIsCyclic() {

        record Item(String key, List<String> deps) {
        }
        List<Item> items = List.of(
                new Item("a", List.of("b")), new Item("b", List.of("a")), new Item(null, List.of()));

        var ordered = TopologicalUtil.sort(items, Item::key, Item::deps);

        assertEquals(1, ordered.waves().size());
        assertEquals(1, ordered.waves().get(0).size());
        assertEquals(2, ordered.cyclic().size());
    }

    @Test
    void aCleanPartOfTheGraphStillGetsOrderedAroundACycle() {

        var ordered = sort(Map.of("x", List.of(), "y", List.of("x"), "a", List.of("b"), "b", List.of("a")));

        assertEquals(List.of(List.of("x"), List.of("y")), ordered.waves());
        assertEquals(2, ordered.cyclic().size());
    }
}
