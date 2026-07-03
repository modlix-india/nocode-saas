package com.modlix.saas.adzump.platform;

import java.util.List;

/**
 * The input to {@link AdPlatform#searchKeywords} (Google keyword ideas). P1 keeps it lean: seed
 * terms and/or a seed URL. Language and geo constraints (Google keyword-plan requires a language
 * constant and geo-target constants) arrive with the J4 Google slice.
 *
 * @param seeds seed terms to expand from (never null; empty is legal when {@code url} is set).
 * @param url   an optional seed landing-page URL to derive ideas from.
 */
public record KeywordSeed(List<String> seeds, String url) {

    public KeywordSeed {
        seeds = seeds == null ? List.of() : List.copyOf(seeds);
    }
}
