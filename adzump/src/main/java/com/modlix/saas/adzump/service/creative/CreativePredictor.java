package com.modlix.saas.adzump.service.creative;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

import org.springframework.stereotype.Service;

import com.modlix.saas.adzump.model.Creative;

/**
 * J20 §5.3 — the pre-spend predictor A4 gates on. Scores a <i>draft</i> creative's likely performance
 * 0..1 from its attributes, read against the account's learned {@link AttributeAttribution} map (the
 * model state), the {@link MarketPriors} (J19 cold-start seed), and the vertical baseline.
 *
 * <p><b>A calibrated heuristic, never an LLM</b> (the mlops ML port is later — §5.3). Per axis, the
 * value's regularized standing is blended toward the baseline by its confidence (an unproven value pulls
 * the estimate to average, not to its noisy extreme), then penalized by its junk correlation. The draft
 * score is the mean of the per-axis estimates; the draft confidence is the mean per-axis confidence.
 *
 * <p><b>Cold start soft-ranks.</b> When the map is prior/default-seeded the score still ranks drafts
 * (a seeded weak creative lands low), but {@link CreativePrediction#coldStart()} / low confidence tells
 * A4 to soft-rank rather than hard-block — so a thin account is not frozen out of trying creatives
 * (§5.3).
 */
@Service
public class CreativePredictor {

    /** Confidence assigned to a value known only from a market prior (seed, not realized). */
    static final double PRIOR_CONFIDENCE = 0.15d;

    /** Draft confidence at/below which A4 should soft-rank rather than hard-block. */
    static final double COLD_CONFIDENCE = 0.30d;

    /** How hard a junk-correlated attribute drags a draft's expected performance (per unit correlation). */
    static final double JUNK_PENALTY = 0.5d;

    private final MarketPriors marketPriors;

    public CreativePredictor(MarketPriors marketPriors) {
        this.marketPriors = marketPriors;
    }

    /**
     * Predicts {@code draft}'s expected performance against the account's attribution map. An untagged
     * draft (no attributes) returns the neutral baseline at zero confidence (cold start).
     */
    public CreativePrediction predict(Creative draft, AttributeAttribution attribution) {

        String vertical = attribution == null ? null : attribution.vertical();
        double baseline = attribution == null ? AttributeAttributor.NEUTRAL_BASELINE : attribution.baseline();
        double baselineNorm = norm(baseline);

        Map<String, String> attributes = draft == null ? null : draft.getAttributes();

        if (attributes == null || attributes.isEmpty())
            return new CreativePrediction(baselineNorm, 0.0d, true,
                    List.of("No attributes tagged; defaulting to the account baseline."));

        double scoreSum = 0.0d;
        double confSum = 0.0d;
        int n = 0;
        List<String> rationale = new ArrayList<>();

        for (Map.Entry<String, String> attr : attributes.entrySet()) {
            String axis = attr.getKey();
            String value = attr.getValue();

            AttributeStat stat = attribution == null ? null : attribution.stat(axis, value);

            double valueScore;
            double valueConf;
            double junk;

            if (stat != null && stat.confidence() > 0) {
                valueScore = stat.regularizedScore();
                valueConf = stat.confidence();
                junk = Math.max(0.0d, stat.junkCorrelation());
            } else {
                OptionalDouble prior = this.marketPriors.priorScore(vertical, axis, value);
                if (prior.isPresent()) {
                    valueScore = prior.getAsDouble();
                    valueConf = PRIOR_CONFIDENCE;
                } else {
                    valueScore = baseline; // genuinely unknown -> neutral, no confidence
                    valueConf = 0.0d;
                }
                junk = 0.0d;
            }

            // Blend the value's own estimate toward the baseline by how much we trust it, then dock junk.
            double axisEstimate = valueConf * norm(valueScore) + (1.0d - valueConf) * baselineNorm;
            axisEstimate = clamp01(axisEstimate - JUNK_PENALTY * junk);

            scoreSum += axisEstimate;
            confSum += valueConf;
            n++;

            rationale.add(rationale(axis, value, stat, valueConf));
        }

        double score = clamp01(scoreSum / n);
        double confidence = clamp01(confSum / n);
        boolean coldStart = (attribution != null && attribution.coldStart()) || confidence <= COLD_CONFIDENCE;

        return new CreativePrediction(round(score), round(confidence), coldStart, rationale);
    }

    /** Convenience: the bare 0..1 score for callers that only need the number. */
    public double predictScore(Creative draft, AttributeAttribution attribution) {
        return this.predict(draft, attribution).score();
    }

    // ==============================================================================================

    private static String rationale(String axis, String value, AttributeStat stat, double conf) {
        if (stat != null && stat.confidence() > 0) {
            String verdict = stat.winner() ? "winning" : (stat.lift() < 0 ? "underperforming" : "neutral");
            return String.format("%s=%s: %s (regScore %.1f, conf %.2f, junkCorr %.2f)",
                    axis, value, verdict, stat.regularizedScore(), stat.confidence(), stat.junkCorrelation());
        }
        return String.format("%s=%s: %s", axis, value, conf > 0 ? "market prior only" : "no evidence (explore)");
    }

    private static double norm(double score0to100) {
        return clamp01(score0to100 / 100.0d);
    }

    private static double clamp01(double v) {
        return Math.max(0.0d, Math.min(1.0d, v));
    }

    private static double round(double v) {
        return Math.round(v * 1_000_000.0d) / 1_000_000.0d;
    }
}
