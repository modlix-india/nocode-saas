package com.modlix.saas.adzump.service.creative;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

import org.junit.jupiter.api.Test;

import com.modlix.saas.adzump.model.Creative;

/**
 * Offline unit tests for {@link CreativePredictor} — the pre-spend predict gate (J20 §5.3). Verifies the
 * gate <b>ranks a seeded weak creative low</b> (below a strong one and below the mid-line) and that
 * <b>cold start soft-ranks</b>: with no realized map the score leans on priors/defaults and surfaces low
 * confidence so A4 does not hard-block a thin account.
 */
class CreativePredictorTest {

    private static final String RE = "real_estate";

    /** A priors source keyed by "axis|value" for the cold-start path. */
    private record Priors(Map<String, Double> scores) implements MarketPriors {
        @Override
        public OptionalDouble priorScore(String vertical, String axis, String value) {
            Double s = this.scores.get(axis + "|" + value);
            return s == null ? OptionalDouble.empty() : OptionalDouble.of(s);
        }

        @Override
        public OptionalDouble priorBaseline(String vertical) {
            return OptionalDouble.empty();
        }
    }

    private static Creative draft(String axis, String value) {
        return new Creative().setId("draft").setAttributes(Map.of(axis, value));
    }

    private static AttributeStat stat(String axis, String value, double regScore, double confidence, boolean winner) {
        // baseline 60, lift = regScore - 60, volume/count broad enough to be trusted, no junk.
        return new AttributeStat(axis, value, regScore, 60.0, regScore - 60.0, 200L, 4, confidence, 0.0, winner,
                false);
    }

    private static AttributeAttribution mapWith(List<AttributeStat> stats, boolean coldStart) {
        return new AttributeAttribution("CLI0", RE, null, 60.0, stats, List.of(), coldStart);
    }

    // ---- tests -----------------------------------------------------------------------------------

    @Test
    void predictGate_ranksASeededWeakCreativeBelowAStrongOne() {

        CreativePredictor predictor = new CreativePredictor(new Priors(Map.of()));

        AttributeAttribution map = mapWith(List.of(
                stat("angle", "investment_roi", 90.0, 0.8, true),   // proven winner
                stat("angle", "scarcity", 30.0, 0.8, false)),        // proven weak
                false);

        CreativePrediction strong = predictor.predict(draft("angle", "investment_roi"), map);
        CreativePrediction weak = predictor.predict(draft("angle", "scarcity"), map);

        assertTrue(weak.score() < strong.score(), "the weak angle must rank below the strong one");
        assertTrue(weak.score() < 0.5, "the seeded weak creative should land below the mid-line");
        assertTrue(strong.score() > 0.5, "the winning angle should land above the mid-line");
        // Both are confidently scored (rich map) -> not a cold-start soft-rank.
        assertTrue(strong.confidence() > CreativePredictor.COLD_CONFIDENCE);
    }

    @Test
    void coldStart_leansOnPriors_andSurfacesLowConfidence() {

        // No realized stats; a market prior says investment_roi is strong (80/100).
        CreativePredictor predictor = new CreativePredictor(new Priors(Map.of("angle|investment_roi", 80.0)));
        AttributeAttribution coldMap = mapWith(List.of(), true);

        CreativePrediction pred = predictor.predict(draft("angle", "investment_roi"), coldMap);

        assertTrue(pred.coldStart(), "no realized evidence -> cold start (A4 soft-ranks)");
        assertTrue(pred.confidence() <= CreativePredictor.COLD_CONFIDENCE, "confidence must be low on prior-only data");
        // The prior (80) still pulls the estimate above the neutral baseline (60 -> 0.60), so drafts rank.
        assertTrue(pred.score() > 0.60, "the prior should lift the estimate above the bare baseline");
    }

    @Test
    void coldStart_noPriorNoStats_returnsNeutralBaselineAtZeroConfidence() {

        CreativePredictor predictor = new CreativePredictor(new Priors(Map.of()));
        AttributeAttribution coldMap = mapWith(List.of(), true);

        CreativePrediction pred = predictor.predict(draft("angle", "brand_new_untested_value"), coldMap);

        assertEquals(0.60d, pred.score(), 1e-9);   // baseline 60 -> 0.60, nothing to shift it
        assertEquals(0.0d, pred.confidence(), 1e-9);
        assertTrue(pred.coldStart());
    }

    @Test
    void untaggedDraft_returnsBaselineColdStart() {

        CreativePredictor predictor = new CreativePredictor(new Priors(Map.of()));
        CreativePrediction pred = predictor.predict(new Creative().setId("bare"), mapWith(List.of(), false));

        assertEquals(0.60d, pred.score(), 1e-9);
        assertEquals(0.0d, pred.confidence(), 1e-9);
        assertTrue(pred.coldStart());
    }
}
