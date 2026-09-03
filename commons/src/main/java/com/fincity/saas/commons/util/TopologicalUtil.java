package com.fincity.saas.commons.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Orders a set of items so that nothing is processed before the things it
 * depends on.
 * <p>
 * Used by transport import, where saving a Storage resolves the Schema it
 * refers to and every Storage its relations point at, and a missing one is a
 * hard failure rather than a warning.
 */
public class TopologicalUtil {

    /**
     * The result of a sort.
     *
     * @param waves  dependency levels. Everything in wave <i>n</i> depends only
     *               on items in waves <i>0..n-1</i>, and items inside one wave
     *               are independent of each other, so a wave can be processed
     *               concurrently as long as the waves themselves are processed
     *               in order.
     * @param cyclic everything the sort could not drain: members of a
     *               dependency cycle, and anything downstream of one. Returned
     *               rather than thrown so the caller can still do something
     *               with them.
     */
    public record Ordered<T>(List<List<T>> waves, List<T> cyclic) {
    }

    /**
     * Kahn's algorithm, returning dependency levels instead of one flat order.
     * <p>
     * Two rules worth knowing about the edges it builds:
     * <ul>
     * <li>A dependency naming something outside {@code items} is dropped. A
     * transport only orders what it carries; anything else has to already exist
     * in the target, and if it does not, the save itself is the right place to
     * say so.</li>
     * <li>A self dependency is <b>kept</b>, so a self referring item comes back
     * in {@code cyclic} as a one node cycle. It genuinely cannot be saved in
     * one pass, and the caller may know how to break it.</li>
     * </ul>
     * Insertion order is preserved inside each wave and inside {@code cyclic},
     * so the same input always produces the same order.
     *
     * @param keyFn        the unique key of an item
     * @param dependencyFn the keys this item must be processed after
     */
    public static <T> Ordered<T> sort(
            List<T> items, Function<T, String> keyFn, Function<T, Collection<String>> dependencyFn) {

        if (items == null || items.isEmpty()) return new Ordered<>(List.of(), List.of());

        // Keyed by name, but holding a list rather than one item. Nothing is
        // ever dropped for having a duplicate key or no key at all: an item
        // this cannot order still has to come out the other side, so the
        // caller decides what to do with it, rather than it vanishing from an
        // import with nothing said.
        Map<String, List<T>> byKey = new LinkedHashMap<>();
        List<T> unkeyed = new ArrayList<>();

        for (T item : items) {
            String key = keyFn.apply(item);
            if (key == null) unkeyed.add(item);
            else byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
        }

        Map<String, Set<String>> dependsOn = new LinkedHashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();
        buildEdges(byKey, dependencyFn, dependsOn, dependents);

        List<List<T>> waves = new ArrayList<>();
        Set<String> pending = new LinkedHashSet<>(byKey.keySet());

        while (!pending.isEmpty()) {

            List<String> ready = new ArrayList<>();
            for (String key : pending) if (dependsOn.get(key).isEmpty()) ready.add(key);

            // Nothing left with all its dependencies met, so what remains is a
            // cycle plus whatever waits behind it.
            if (ready.isEmpty()) break;

            List<T> wave = new ArrayList<>(ready.size());
            for (String key : ready) {
                wave.addAll(byKey.get(key));
                pending.remove(key);
                for (String dependent : dependents.getOrDefault(key, List.of()))
                    dependsOn.get(dependent).remove(key);
            }

            waves.add(wave);
        }

        // Nothing can depend on an item with no key, so it goes as early as
        // anything else can.
        if (!unkeyed.isEmpty()) {
            if (waves.isEmpty()) waves.add(unkeyed);
            else waves.get(0).addAll(unkeyed);
        }

        List<T> cyclic = new ArrayList<>();
        for (String key : pending) cyclic.addAll(byKey.get(key));

        return new Ordered<>(waves, cyclic);
    }

    /**
     * Fills in, for each key, the keys it waits on and the keys waiting on it.
     * Dependencies naming something outside the set are dropped; everything
     * sharing a key counts as one node, so it waits on the union of what they
     * each name.
     */
    private static <T> void buildEdges(
            Map<String, List<T>> byKey,
            Function<T, Collection<String>> dependencyFn,
            Map<String, Set<String>> dependsOn,
            Map<String, List<String>> dependents) {

        for (Map.Entry<String, List<T>> entry : byKey.entrySet()) {

            Set<String> resolved = new HashSet<>();

            for (T item : entry.getValue()) {
                Collection<String> deps = dependencyFn.apply(item);
                if (deps == null) continue;
                for (String dep : deps) if (byKey.containsKey(dep)) resolved.add(dep);
            }

            dependsOn.put(entry.getKey(), resolved);

            for (String dep : resolved)
                dependents.computeIfAbsent(dep, k -> new ArrayList<>()).add(entry.getKey());
        }
    }

    private TopologicalUtil() {}
}
