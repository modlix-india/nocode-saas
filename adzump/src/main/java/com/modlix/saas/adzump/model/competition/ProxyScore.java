package com.modlix.saas.adzump.model.competition;

/**
 * The decomposed "best-working proxy" score for one competitor ad (J19 §5.2). Every component is a
 * normalized {@code 0..1} signal; {@link #total} is their weight-blended sum (also {@code 0..1} when
 * the weights sum to 1). The decomposition is kept — not just the total — so the Competition tab can
 * explain <i>why</i> an ad ranked ("running 140 days, 6 re-cuts, still active"), and so the honest
 * caveat stays legible: none of these are performance, they are belief-revealed signals.
 *
 * @param longevity  continuous days running, saturating (a long-lived ad is unlikely to be pure waste).
 * @param iteration  near-variant density from the same advertiser on the same theme (investment tell).
 * @param recency    currently-active / recently-stopped (active beats long-stopped).
 * @param reachBonus reach signal — {@code 0} unless the ad carries reach (political/issue only).
 * @param breadth    the same theme corroborated across multiple competitors (market-level signal).
 * @param total      the weight-blended proxy total used for ranking.
 */
public record ProxyScore(
        double longevity,
        double iteration,
        double recency,
        double reachBonus,
        double breadth,
        double total) {
}
