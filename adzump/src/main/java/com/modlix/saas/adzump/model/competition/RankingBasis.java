package com.modlix.saas.adzump.model.competition;

/**
 * How a competition shortlist was ranked. J19 only ever produces {@link #PROXY}: the Ad Library has no
 * spend/impressions/conversions for commercial ads, so the ranking is a belief-revealed proxy, not a
 * measurement (J19 §5.4). This is stamped on every result and every persisted body so nothing
 * downstream — A4 grounding, the Competition tab — can mistake it for performance.
 *
 * <p>The enum has one member deliberately: it is a labeled contract, not a toggle. A future
 * {@code PERFORMANCE} basis would only ever come from the real loop (leadzump outcomes, J10/J20), never
 * from the Ad Library.
 */
public enum RankingBasis {

    /** Belief-revealed longevity/iteration/recency/reach/breadth proxy. NOT performance. */
    PROXY
}
