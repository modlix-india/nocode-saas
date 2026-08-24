package com.fincity.security.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jooq.types.ULong;

/**
 * One client's reporting lines, resolved once and answered from memory.
 *
 * <p>Held in the platform cache under {@code orgStructure:<clientId>}. It exists because the same
 * tree has to be read in two directions and neither was cheap before:
 *
 * <ul>
 *   <li><b>Downward</b> ("whose deals may I see?") was a walk that issued one cache lookup per
 *       person in the sub-tree. For a manager near the top of a deep organisation that is hundreds
 *       of round trips, paid on every deal read.
 *   <li><b>Upward</b> ("who may see this person's deals?") had no implementation at all. It is the
 *       question that decides who hears about an incoming WhatsApp message, and answering it by
 *       asking every connected browser to try reading the deal is what this replaces.
 * </ul>
 *
 * <p>Both are now walks over two maps, and the upward one is bounded by the depth of the tree rather
 * than by the size of it.
 *
 * <h2>What this deliberately does not do</h2>
 *
 * <p><b>It does not filter on status.</b> The query it replaced did not either, so a sub-org has
 * always included deactivated people and a manager has always kept seeing a departed report's
 * deals. Changing that here would silently change what every deal query returns, which is not this
 * change's business. {@link #isActive(ULong)} is provided so a caller who genuinely needs live
 * people, such as the one picking who to notify, can ask.
 *
 * <p><b>It is one client's tree, not a tenant's.</b> Reporting lines do not cross clients: the
 * queries behind it are all scoped {@code CLIENT_ID = ?}. A business partner arrangement is
 * expressed through client hierarchy, not through {@code reportingTo}, and is resolved elsewhere.
 *
 * <h2>Staleness</h2>
 *
 * <p>Cached until something evicts it. Every write that changes a reporting line, moves a user
 * between clients, or changes a user's status must evict {@code orgStructure:<clientId>}; see
 * {@code OrgStructureService.evict}. A stale tree here is not a slow page, it is a message reaching
 * somebody who has left the team, so the eviction list is part of the contract rather than an
 * optimisation.
 */
public class OrgStructure implements Serializable {

    @Serial
    private static final long serialVersionUID = 4477190664403461293L;

    /** Guards against a cycle in the data. A reporting loop would otherwise hang the walk. */
    private static final int MAX_DEPTH = 64;

    private final Map<ULong, ULong> managerOf;
    private final Map<ULong, List<ULong>> reportsOf;
    private final Set<ULong> inactive;

    public OrgStructure(Map<ULong, ULong> managerOf, Set<ULong> inactive) {

        // A HashMap rather than Map.copyOf: someone reporting to nobody is stored with a null
        // value, and the immutable factories reject nulls. Their key still has to be present, since
        // the key set is how contains() answers "is this person in this client at all".
        this.managerOf = managerOf == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(managerOf));
        this.inactive = inactive == null ? Set.of() : Set.copyOf(inactive);

        Map<ULong, List<ULong>> children = new HashMap<>();
        this.managerOf.forEach((user, manager) -> {
            if (manager != null) children.computeIfAbsent(manager, k -> new ArrayList<>()).add(user);
        });
        children.replaceAll((k, v) -> List.copyOf(v));
        this.reportsOf = Collections.unmodifiableMap(children);
    }

    /** Everyone below this person, plus optionally the person. Order is unspecified. */
    public Set<ULong> subOrg(ULong userId, boolean includeSelf) {

        if (userId == null) return Set.of();

        Set<ULong> found = new LinkedHashSet<>();
        Deque<ULong> pending = new ArrayDeque<>();
        pending.add(userId);

        while (!pending.isEmpty()) {
            ULong current = pending.poll();
            if (!found.add(current)) continue;
            List<ULong> reports = this.reportsOf.get(current);
            if (reports != null) pending.addAll(reports);
        }

        if (!includeSelf) found.remove(userId);
        return found;
    }

    /**
     * This person and everyone they report to, transitively.
     *
     * <p>The set of people for whom this person is inside their sub-org, which is exactly the set of
     * people entitled to their records under the deal read rule.
     */
    public Set<ULong> selfAndManagers(ULong userId) {

        if (userId == null) return Set.of();

        Set<ULong> chain = new LinkedHashSet<>();
        ULong current = userId;
        int guard = 0;

        while (current != null && chain.add(current) && guard++ < MAX_DEPTH) current = this.managerOf.get(current);

        return Collections.unmodifiableSet(chain);
    }

    /** Whether this client's tree knows the user at all. */
    public boolean contains(ULong userId) {
        return userId != null && this.managerOf.containsKey(userId);
    }

    /** False for a user this client's tree has never heard of, so callers fail closed. */
    public boolean isActive(ULong userId) {
        return this.contains(userId) && !this.inactive.contains(userId);
    }

    public int size() {
        return this.managerOf.size();
    }

    /** Only the active ones, for callers deciding who to tell rather than what to show. */
    public Set<ULong> activeOnly(Set<ULong> userIds) {
        if (userIds == null || userIds.isEmpty()) return Set.of();
        Set<ULong> live = new HashSet<>(userIds);
        live.removeIf(id -> !this.isActive(id));
        return live;
    }
}
