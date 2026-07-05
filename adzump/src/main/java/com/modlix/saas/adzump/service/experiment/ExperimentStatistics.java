package com.modlix.saas.adzump.service.experiment;

/**
 * J21 §5.3 — the pure significance math behind an {@link com.modlix.saas.adzump.dto.ExperimentReadout}. A
 * variant's blended creative-grain score (0..100) is treated as a success proportion {@code p = score/100}
 * and its CRM outcome {@code volume} as the sample size {@code n} (the documented statistical weight of
 * {@link com.modlix.saas.adzump.service.creative.CreativeScore#volume()}). Two arms are then compared with a
 * <b>two-proportion z-test</b>, and each arm's score gets a Wald confidence interval.
 *
 * <p>No I/O, no state, no randomness — every method is a deterministic function of its arguments, so the
 * readout math is unit-testable from hand-built numbers. The normal CDF uses the Numerical-Recipes
 * {@code erfc} approximation (fractional error &lt; 1.2e-7), which is far tighter than any decision this
 * gate makes.
 */
final class ExperimentStatistics {

    /** Two-sided z for a 95% confidence interval / a 0.05 significance threshold. */
    static final double Z_95 = 1.959963985d;

    /** The significance threshold p-values are compared against. */
    static final double ALPHA = 0.05d;

    private ExperimentStatistics() {
    }

    /**
     * The two-proportion pooled z-statistic comparing arm 1 ({@code p1},{@code n1}) to arm 2
     * ({@code p2},{@code n2}). Returns {@code 0} (no separation) when either arm has no volume or the pooled
     * variance is degenerate, so a thin-data comparison can never read as significant.
     */
    static double zTwoProportion(double p1, long n1, double p2, long n2) {
        if (n1 <= 0L || n2 <= 0L)
            return 0.0d;
        double pooled = (p1 * n1 + p2 * n2) / (n1 + n2);
        double se = Math.sqrt(pooled * (1.0d - pooled) * (1.0d / n1 + 1.0d / n2));
        if (se <= 0.0d)
            return 0.0d;
        return (p1 - p2) / se;
    }

    /** The two-tailed p-value of a z-statistic under the standard normal. */
    static double twoTailedPValue(double z) {
        double p = 2.0d * (1.0d - normalCdf(Math.abs(z)));
        if (p < 0.0d)
            return 0.0d;
        return Math.min(p, 1.0d);
    }

    /**
     * The 95% Wald confidence interval {@code p ± Z_95 * sqrt(p(1-p)/n)} for a proportion, clamped to
     * {@code [0,1]}. A zero-volume arm yields the widest interval {@code [0,1]} (no information).
     */
    static double[] waldInterval(double p, long n) {
        if (n <= 0L)
            return new double[] { 0.0d, 1.0d };
        double half = Z_95 * Math.sqrt(p * (1.0d - p) / n);
        return new double[] { clamp01(p - half), clamp01(p + half) };
    }

    /** The standard-normal CDF, via the complementary error function. */
    static double normalCdf(double x) {
        return 0.5d * erfc(-x / Math.sqrt(2.0d));
    }

    /** Numerical-Recipes {@code erfcc}: complementary error function, fractional error &lt; 1.2e-7. */
    private static double erfc(double x) {
        double z = Math.abs(x);
        double t = 1.0d / (1.0d + 0.5d * z);
        double ans = t * Math.exp(-z * z - 1.26551223d + t * (1.00002368d + t * (0.37409196d + t * (0.09678418d
                + t * (-0.18628806d + t * (0.27886807d + t * (-1.13520398d + t * (1.48851587d
                        + t * (-0.82215223d + t * 0.17087277d))))))))); // NR 6.2
        return x >= 0.0d ? ans : 2.0d - ans;
    }

    private static double clamp01(double v) {
        if (v < 0.0d)
            return 0.0d;
        return Math.min(v, 1.0d);
    }
}
