package com.modlix.saas.adzump.platform;

/**
 * A single optimization lever the loop may act on for a given campaign type. Which levers a
 * type actually exposes is data-driven via {@link PlatformCapabilities#optimizationFor} — a
 * transparent Search campaign exposes the full set (keyword/negative/placement/audience/bid/
 * budget/creative), while an opaque Performance Max / Advantage+ campaign exposes only the thin
 * set the platform allows (budget, goal, audience-signal, asset-group, listing-group). The loop
 * reads the profile instead of hardcoding "Search only", so no type is ever blocked for lacking
 * surgical control.
 */
public enum Lever {
    BUDGET,
    BID,
    AUDIENCE,
    KEYWORD,
    NEGATIVE_KEYWORD,
    PLACEMENT,
    CREATIVE_ROTATE,
    ASSET_GROUP,
    AUDIENCE_SIGNAL,
    LISTING_GROUP,
    GOAL
}
