package com.modlix.saas.adzump.platform;

import java.util.Set;

import com.modlix.saas.adzump.model.leadzump.Grain;

/**
 * What the loop may do to a given campaign type: the set of {@link Lever}s it can act on, plus the
 * finest grain that type reports at. This is how "manage every campaign type; optimize whatever a
 * type exposes" stays data-driven — a transparent type carries the full lever set and an AD grain,
 * an opaque one (PMax / Advantage+) carries a thin lever set and a CAMPAIGN grain. The loop
 * degrades gracefully: it optimizes at whatever levers + grain the type allows and still attributes
 * CRM outcomes at the finest reported grain.
 *
 * @param levers               the levers this type exposes (never null; empty is legal).
 * @param finestReportingGrain the finest grain this type reports metrics at.
 */
public record OptimizationProfile(
        Set<Lever> levers,
        Grain finestReportingGrain) {

    public OptimizationProfile {
        levers = levers == null ? Set.of() : Set.copyOf(levers);
    }

    public boolean allows(Lever lever) {
        return this.levers.contains(lever);
    }
}
