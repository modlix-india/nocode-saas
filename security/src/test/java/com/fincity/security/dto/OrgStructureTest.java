package com.fincity.security.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jooq.types.ULong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The cached reporting tree.
 *
 * <p>Two properties matter here and the rest is detail.
 *
 * <p><b>Downward has to match what it replaced.</b> {@code subOrg} took over from an
 * {@code expandDeep} walk over {@code getLevel1SubOrg}, and that walk is what every deal query's
 * access condition is built from. A difference here is a difference in what customers can see, so
 * {@link #matchesTheWalkItReplaced} reimplements the old traversal and asserts the two agree on a
 * deep tree.
 *
 * <p><b>Upward and downward have to be consistent with each other.</b> The whole point of the
 * inversion is that "who may see this person's records" can be answered from the other end.
 * {@link #upwardIsTheExactInverseOfDownward} asserts that for every pair, which is the property the
 * WhatsApp audience resolver leans on.
 */
class OrgStructureTest {

    private static ULong u(int id) {
        return ULong.valueOf(id);
    }

    /**
     * A ten-level chain with branching, which is the shape that motivated this change.
     *
     * <pre>
     * 1
     * ├── 2 ── 4 ── 6 ── 8 ── 10 ── 12 ── 14 ── 16 ── 18
     * └── 3 ── 5 ── 7 ── 9 ── 11
     * 20 reports to nobody (a second root)
     * </pre>
     */
    private static OrgStructure deepTree() {
        Map<ULong, ULong> managerOf = new LinkedHashMap<>();
        managerOf.put(u(1), null);
        managerOf.put(u(2), u(1));
        managerOf.put(u(3), u(1));
        managerOf.put(u(4), u(2));
        managerOf.put(u(5), u(3));
        managerOf.put(u(6), u(4));
        managerOf.put(u(7), u(5));
        managerOf.put(u(8), u(6));
        managerOf.put(u(9), u(7));
        managerOf.put(u(10), u(8));
        managerOf.put(u(11), u(9));
        managerOf.put(u(12), u(10));
        managerOf.put(u(14), u(12));
        managerOf.put(u(16), u(14));
        managerOf.put(u(18), u(16));
        managerOf.put(u(20), null);
        return new OrgStructure(managerOf, Set.of(u(9)));
    }

    /** The traversal this class replaced, reimplemented so the two can be compared. */
    private static Set<ULong> oldWalk(Map<ULong, ULong> managerOf, ULong root, boolean includeSelf) {

        Map<ULong, List<ULong>> level1 = new HashMap<>();
        managerOf.forEach((user, manager) -> {
            if (manager != null) level1.computeIfAbsent(manager, k -> new java.util.ArrayList<>()).add(user);
        });

        Set<ULong> visited = new HashSet<>();
        java.util.Deque<ULong> pending = new java.util.ArrayDeque<>();
        pending.add(root);

        while (!pending.isEmpty()) {
            ULong current = pending.poll();
            if (!visited.add(current)) continue;
            pending.addAll(level1.getOrDefault(current, List.of()));
        }

        if (!includeSelf) visited.remove(root);
        return visited;
    }

    @Test
    @DisplayName("subOrg returns exactly what the per-node walk returned")
    void matchesTheWalkItReplaced() {

        Map<ULong, ULong> managerOf = new LinkedHashMap<>();
        managerOf.put(u(1), null);
        managerOf.put(u(2), u(1));
        managerOf.put(u(3), u(1));
        managerOf.put(u(4), u(2));
        managerOf.put(u(5), u(3));
        managerOf.put(u(6), u(4));
        managerOf.put(u(7), u(5));
        managerOf.put(u(8), u(6));
        managerOf.put(u(9), u(7));
        managerOf.put(u(10), u(8));
        managerOf.put(u(11), u(9));
        managerOf.put(u(12), u(10));
        managerOf.put(u(14), u(12));
        managerOf.put(u(16), u(14));
        managerOf.put(u(18), u(16));
        managerOf.put(u(20), null);

        OrgStructure tree = new OrgStructure(managerOf, Set.of());

        for (ULong user : managerOf.keySet()) {
            assertEquals(oldWalk(managerOf, user, true), tree.subOrg(user, true), "includeSelf, from " + user);
            assertEquals(oldWalk(managerOf, user, false), tree.subOrg(user, false), "excludeSelf, from " + user);
        }
    }

    @Test
    @DisplayName("Upward is the exact inverse of downward, for every pair")
    void upwardIsTheExactInverseOfDownward() {

        OrgStructure tree = deepTree();
        Set<ULong> everyone = tree.subOrg(u(1), true);
        everyone.add(u(20));

        for (ULong subject : everyone) {
            for (ULong viewer : everyone) {
                boolean seenFromAbove = tree.subOrg(viewer, true).contains(subject);
                boolean seesFromBelow = tree.selfAndManagers(subject).contains(viewer);

                // This equivalence is the entire basis for resolving an audience by walking up
                // instead of expanding down. If it ever fails, the WhatsApp audience is wrong.
                assertEquals(
                        seenFromAbove,
                        seesFromBelow,
                        "viewer " + viewer + " vs subject " + subject);
            }
        }
    }

    @Test
    @DisplayName("The upward chain is bounded by depth, not by the size of the organisation")
    void upwardIsShort() {

        OrgStructure tree = deepTree();

        // 18 -> 16 -> 14 -> 12 -> 10 -> 8 -> 6 -> 4 -> 2 -> 1, plus itself.
        assertEquals(10, tree.selfAndManagers(u(18)).size());
        assertTrue(tree.selfAndManagers(u(18)).contains(u(1)));
        assertTrue(tree.selfAndManagers(u(18)).contains(u(18)));
        assertFalse(tree.selfAndManagers(u(18)).contains(u(3)), "a sibling branch is not in the chain");
    }

    @Test
    @DisplayName("A reporting cycle terminates instead of hanging")
    void survivesACycle() {

        Map<ULong, ULong> managerOf = new LinkedHashMap<>();
        managerOf.put(u(1), u(2));
        managerOf.put(u(2), u(3));
        managerOf.put(u(3), u(1));

        OrgStructure tree = new OrgStructure(managerOf, Set.of());

        // Bad data, not a supported state, but a walk that never returns takes the service with it.
        assertEquals(3, tree.selfAndManagers(u(1)).size());
        assertEquals(3, tree.subOrg(u(1), true).size());
    }

    @Test
    @DisplayName("Status is carried but never filters the tree")
    void statusIsAvailableButNotApplied() {

        OrgStructure tree = deepTree();

        // 9 is inactive. It must still appear in its manager's sub-org, because the query this
        // replaced had no status filter and deal reads depend on that: a manager keeps seeing a
        // departed report's deals.
        assertTrue(tree.subOrg(u(7), true).contains(u(9)), "reads must not change");
        assertFalse(tree.isActive(u(9)), "but a caller choosing who to notify can tell");
        assertTrue(tree.isActive(u(7)));

        assertEquals(Set.of(u(7)), tree.activeOnly(Set.of(u(7), u(9))));
    }

    @Test
    @DisplayName("A user this client has never heard of is not active and not contained")
    void unknownUsersFailClosed() {

        OrgStructure tree = deepTree();

        assertFalse(tree.contains(u(999)));
        assertFalse(tree.isActive(u(999)), "unknown must not read as active, or audiences fail open");
        assertEquals(Set.of(), tree.selfAndManagers(null));
        assertEquals(Set.of(), tree.subOrg(null, true));
    }

    @Test
    @DisplayName("Someone reporting to nobody is their own whole chain")
    void rootHasOnlyItself() {

        OrgStructure tree = deepTree();

        assertEquals(Set.of(u(20)), tree.selfAndManagers(u(20)));
        assertEquals(Set.of(u(20)), tree.subOrg(u(20), true));
    }
}
