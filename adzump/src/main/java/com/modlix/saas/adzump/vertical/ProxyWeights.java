package com.modlix.saas.adzump.vertical;

/**
 * The J19 "best-working proxy" weights — vertical-tunable knowledge (J5), never hardcoded in the
 * scorer. Each weight scales one 0..1 proxy signal into the blended total; the design (J19 §5.2)
 * fixes the <b>relative</b> emphasis: longevity is weighted most, iteration density second, then
 * recency, then the two market-corroboration signals (reach, breadth).
 *
 * <p>These live here (a {@link VerticalPlaybook} may supply its own via
 * {@link VerticalPlaybook#competitionProxyWeights()}) rather than in the scorer, mirroring how
 * {@link PolicyDefaults} keeps platform defaults out of the payload layer: the "best-working" belief
 * is an industry judgement, so a vertical can retune it (a high-ticket vertical trusts a long-lived
 * ad more than an impulse-buy vertical does). When a vertical supplies none, {@link #defaults()}
 * apply.
 *
 * <p><b>Honest caveat baked into the weighting:</b> reach is weighted low and only ever contributes
 * when present (political/issue ads), and no weight can turn this into a performance measure — the
 * signals are all belief-revealed, never spend/impression/conversion truth (J19 §5.4).
 *
 * @param longevity weight on continuous days running (the dominant signal).
 * @param iteration weight on near-variant density from the same advertiser on the same theme.
 * @param recency   weight on currently-active / recently-stopped.
 * @param reach     weight on the reach bonus (applied only when reach is present).
 * @param breadth   weight on a theme corroborated across multiple competitors.
 */
public record ProxyWeights(
        double longevity,
        double iteration,
        double recency,
        double reach,
        double breadth) {

    public ProxyWeights {
        if (longevity < 0 || iteration < 0 || recency < 0 || reach < 0 || breadth < 0)
            throw new IllegalArgumentException("ProxyWeights must be non-negative");
    }

    /**
     * The vertical-neutral defaults. Longevity dominates and iteration is second, so a long-lived and
     * frequently re-cut theme sorts above a short-lived one-off even before recency/reach/breadth are
     * considered (J19 §5.2). Weights sum to 1.0 so the blended total stays in 0..1.
     */
    public static ProxyWeights defaults() {
        return new ProxyWeights(0.40, 0.25, 0.15, 0.10, 0.10);
    }
}
