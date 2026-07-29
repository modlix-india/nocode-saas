package com.modlix.saas.adzump.vertical;

import java.util.List;

/**
 * Seed lists A3/J3/J4 expand from when proposing targeting for this vertical: Meta interest seeds and
 * Google keyword seeds. These are <b>seeds</b>, not the final targeting — A3 curates and justifies
 * against fetched platform candidates (the id-honesty rule); nothing here is invented at compile time.
 *
 * @param interestSeeds Meta detailed-interest seed terms (expanded against Meta's interest search).
 * @param keywordSeeds  Google keyword seed terms (expanded against Google's keyword ideas).
 */
public record TargetingSeeds(List<String> interestSeeds, List<String> keywordSeeds) {

    public TargetingSeeds {
        interestSeeds = interestSeeds == null ? List.of() : List.copyOf(interestSeeds);
        keywordSeeds = keywordSeeds == null ? List.of() : List.copyOf(keywordSeeds);
    }

    public static TargetingSeeds empty() {
        return new TargetingSeeds(List.of(), List.of());
    }
}
