package com.modlix.saas.adzump.vertical;

import java.util.List;

/**
 * The structured rubric A3's LLM critic scores a drafted plan against. Each {@link Axis} is a scored
 * dimension with a weight; the weighted sum is compared to {@link #passThreshold()} to decide whether
 * the repair loop can stop. Kept as vertical config (a shared base rubric plus vertical overlays) so
 * A3 does not embed quality criteria in a prompt string.
 *
 * @param axes          the scored dimensions and their weights (weights are expected to sum to ~1.0).
 * @param passThreshold the weighted-score threshold the critic must clear (A3's per-vertical THRESHOLD).
 */
public record CriticRubric(List<Axis> axes, double passThreshold) {

    public CriticRubric {
        axes = axes == null ? List.of() : List.copyOf(axes);
    }

    /**
     * One scored dimension of the rubric.
     *
     * @param key         stable axis key the critic returns a per-axis score under (0..1).
     * @param description what "good" looks like on this axis (guides the critic).
     * @param weight      relative weight in the aggregate score.
     */
    public record Axis(String key, String description, double weight) {
    }
}
