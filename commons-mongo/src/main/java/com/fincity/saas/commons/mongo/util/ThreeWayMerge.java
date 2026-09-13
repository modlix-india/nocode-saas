package com.fincity.saas.commons.mongo.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Merge two independent sets of edits made from the same starting point.
 *
 * The shape every overridable object already has -- a JSON tree of nested maps --
 * is what makes this possible without any per-type knowledge. A draft and the live
 * document are two maps that both descend from one base map, and the question at
 * every node is the same: which side moved.
 *
 * The rule is stated once, in {@link #mergeValue}, and it is deliberately biased
 * towards the DRAFT. Where only one side moved, that side wins and the other is
 * untouched. Where both moved to the same value there is nothing to decide. Only
 * where both moved to DIFFERENT values is there a real conflict, and there the
 * draft wins and the path is reported, because the draft is the change someone is
 * asking to publish right now and silently discarding it would be worse than
 * silently discarding a live edit they can still see in version history.
 *
 * Lists are atomic. A list in these definitions is an ordered whole (languages,
 * a condition's operands) rather than a keyed collection -- Modlix keys anything
 * it wants to merge by uuid into a map -- so element-wise merging would invent an
 * ordering nobody asked for. Two sides editing the same list is a conflict.
 */
public final class ThreeWayMerge {

    /**
     * @param merged    the reconciled tree
     * @param conflicts dotted paths where both sides moved to different values and
     *                  the draft was taken. Empty on a clean merge.
     */
    public record Result(Map<String, Object> merged, List<String> conflicts) {

        public boolean isClean() {
            return this.conflicts.isEmpty();
        }
    }

    /**
     * Distinguishes "the key is absent" from "the key is present and null", which
     * is the difference between a deletion and a value, and the only way a key
     * removed on one side survives as a removal rather than reappearing.
     */
    private static final Object MISSING = new Object();

    private ThreeWayMerge() {
    }

    /**
     * @param ignoredRootKeys keys skipped at the ROOT only, and absent from the
     *                        result. Identity and bookkeeping (id, version,
     *                        timestamps) belong to the document rather than to the
     *                        content, and the caller reimposes them; merging them
     *                        would produce a document claiming to be a version it
     *                        is not.
     */
    public static Result merge(Map<String, Object> base, Map<String, Object> live, Map<String, Object> draft,
            Set<String> ignoredRootKeys) {

        List<String> conflicts = new ArrayList<>();

        Map<String, Object> merged = mergeMaps(nullSafe(base), nullSafe(live), nullSafe(draft), "",
                ignoredRootKeys == null ? Set.of() : ignoredRootKeys, conflicts);

        return new Result(merged, conflicts);
    }

    private static Map<String, Object> mergeMaps(Map<String, Object> base, Map<String, Object> live,
            Map<String, Object> draft, String path, Set<String> ignoredRootKeys, List<String> conflicts) {

        // Live first so a key neither side touched keeps the order it has on the
        // live document, and keys only the draft adds land at the end.
        Set<String> keys = new LinkedHashSet<>(live.keySet());
        keys.addAll(draft.keySet());
        keys.addAll(base.keySet());

        Map<String, Object> out = new LinkedHashMap<>();

        for (String key : keys) {

            if (path.isEmpty() && ignoredRootKeys.contains(key))
                continue;

            Object value = mergeValue(
                    base.containsKey(key) ? base.get(key) : MISSING,
                    live.containsKey(key) ? live.get(key) : MISSING,
                    draft.containsKey(key) ? draft.get(key) : MISSING,
                    path.isEmpty() ? key : path + "." + key,
                    ignoredRootKeys, conflicts);

            if (value != MISSING)
                out.put(key, value);
        }

        return out;
    }

    private static Object mergeValue(Object base, Object live, Object draft, String path, Set<String> ignoredRootKeys,
            List<String> conflicts) {

        // The draft did not touch this, so whatever the live document says now is
        // the truth -- including a deletion, and including a change made after the
        // draft was taken. This single line is what pulls newer live content in.
        if (Objects.equals(draft, base))
            return live;

        // Only the draft moved.
        if (Objects.equals(live, base))
            return draft;

        // Both moved, to the same place.
        if (Objects.equals(live, draft))
            return draft;

        // Both moved, and both are still trees: the disagreement may be confined to
        // a subtree, so ask the same question one level down rather than declaring
        // the whole branch a conflict. A base that is missing or was replaced by a
        // scalar is treated as empty, which makes two independently ADDED subtrees
        // merge into their union instead of one of them being thrown away.
        if (live instanceof Map && draft instanceof Map)
            return mergeMaps(base instanceof Map ? asMap(base) : Map.of(), asMap(live), asMap(draft), path,
                    ignoredRootKeys, conflicts);

        conflicts.add(path);
        return draft;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return (Map<String, Object>) o;
    }

    private static Map<String, Object> nullSafe(Map<String, Object> m) {
        return m == null ? Map.of() : m;
    }
}
