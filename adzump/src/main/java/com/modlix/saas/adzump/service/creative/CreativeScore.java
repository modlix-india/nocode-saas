package com.modlix.saas.adzump.service.creative;

import com.modlix.saas.adzump.model.snapshot.SignalMaturity;

/**
 * A creative-grain outcome (J20 §5.1): the blended {@code PerformancePolicy} score for one creative,
 * rolled up from the snapshot AD-grain rows that carry it, alongside the volume and
 * {@link SignalMaturity maturity} that say <b>how much to trust it</b>.
 *
 * <p>The maturity is the discipline the design calls for: a creative is <b>not judged or killed on
 * thin/immature data</b> (J20 §5.1, mirroring J12). {@link #judgeable()} is {@code true} only once the
 * slow leadzump signal has matured ({@link SignalMaturity#MATURE}); downstream (attribution weighting,
 * the predict gate) treats a non-judgeable creative as low-confidence rather than a proven win/loss.
 *
 * @param creativeId    the plan-body creative id (matched to the snapshot AD grain — see
 *                      {@link CreativeScorer}).
 * @param score         the 0..100 blended score, spend-weighted over the creative's AD rows via
 *                      {@link com.modlix.saas.adzump.service.feedback.PolicyScorer#rollup}.
 * @param volume        total CRM outcome count across the matched rows — the statistical weight of this
 *                      creative's evidence (0 when only the fast platform signal has landed).
 * @param junkRate      the volume-weighted leadzump junk fraction (0..1) across the matched rows.
 * @param maturity      the coarsest-trust maturity across the matched rows (the most-mature row wins).
 * @param judgeable     {@code true} iff {@link #maturity()} is {@link SignalMaturity#MATURE}.
 * @param matchedAdRows how many AD-grain snapshot rows joined to this creative (0 = no signal yet).
 */
public record CreativeScore(
        String creativeId,
        double score,
        long volume,
        double junkRate,
        SignalMaturity maturity,
        boolean judgeable,
        int matchedAdRows) {

    /** A no-signal score for a creative with no matched AD rows yet (FAST_ONLY, not judgeable). */
    public static CreativeScore empty(String creativeId) {
        return new CreativeScore(creativeId, 0.0d, 0L, 0.0d, SignalMaturity.FAST_ONLY, false, 0);
    }
}
