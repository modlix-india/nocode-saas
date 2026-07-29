package com.modlix.saas.adzump.vertical;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The creative-attribute taxonomy for a vertical: a set of axes (e.g. {@code angle}, {@code scene},
 * {@code offer}, {@code cta}) each mapping to the allowed values for this industry. A4 tags creatives
 * with values from here and J20 attributes realized outcomes back onto them ("which angle/scene/offer
 * wins"). J6 warns (not hard-fails) on a novel value the loop is exploring.
 *
 * @param axes ordered map of axis name &rarr; allowed values. Empty for the generic vertical.
 */
public record AttributeTaxonomy(Map<String, List<String>> axes) {

    public AttributeTaxonomy {
        axes = axes == null ? Map.of() : Map.copyOf(axes);
    }

    public static AttributeTaxonomy empty() {
        return new AttributeTaxonomy(Map.of());
    }

    public Set<String> axisNames() {
        return axes.keySet();
    }

    public List<String> valuesFor(String axis) {
        return axes.getOrDefault(axis, List.of());
    }

    /** True if {@code value} is a known value on {@code axis} (used by J6's soft taxonomy check). */
    public boolean knows(String axis, String value) {
        return axes.getOrDefault(axis, List.of()).contains(value);
    }
}
