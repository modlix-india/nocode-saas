package com.modlix.saas.adzump.service.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the pure J21 significance math (no I/O, no state): the two-proportion z-test that separates
 * a real creative win from noise, its two-tailed p-value, and the Wald confidence interval — all as
 * deterministic functions of hand-built (score, volume) pairs.
 */
class ExperimentStatisticsTest {

    @Test
    void zTwoProportion_wideSeparation_largeVolumes_isStronglySignificant() {
        // 0.60 vs 0.40 at n=400 each: pooled 0.50, SE ~0.03536, z ~5.66.
        double z = ExperimentStatistics.zTwoProportion(0.60d, 400L, 0.40d, 400L);
        assertTrue(z > 5.0d && z < 6.0d, "z=" + z);

        double p = ExperimentStatistics.twoTailedPValue(z);
        assertTrue(p < 1.0e-3d, "p=" + p);
        assertTrue(p < ExperimentStatistics.ALPHA);
    }

    @Test
    void zTwoProportion_narrowSeparation_isNotSignificant() {
        // 0.52 vs 0.48 at n=350 each: z ~1.06, p ~0.29 — chance, not a win.
        double z = ExperimentStatistics.zTwoProportion(0.52d, 350L, 0.48d, 350L);
        double p = ExperimentStatistics.twoTailedPValue(z);
        assertTrue(p > ExperimentStatistics.ALPHA, "p=" + p);
        assertTrue(p > 0.2d && p < 0.4d, "p=" + p);
    }

    @Test
    void zTwoProportion_zeroVolumeArm_isZero() {
        assertEquals(0.0d, ExperimentStatistics.zTwoProportion(0.9d, 0L, 0.1d, 400L), 1e-12d);
        // erfc is the NR approximation (documented fractional error < 1.2e-7), so p(z=0) is 1.0 to ~3e-8,
        // not to 1e-12; assert at the house tolerance the sibling normalCdf test uses (irrelevant to p<ALPHA).
        assertEquals(1.0d, ExperimentStatistics.twoTailedPValue(0.0d), 1e-6d);
    }

    @Test
    void twoTailedPValue_atCriticalZ_isAboutAlpha() {
        double p = ExperimentStatistics.twoTailedPValue(ExperimentStatistics.Z_95);
        assertTrue(p > 0.04d && p < 0.06d, "p=" + p);
    }

    @Test
    void normalCdf_atZero_isHalf() {
        assertEquals(0.5d, ExperimentStatistics.normalCdf(0.0d), 1e-6d);
    }

    @Test
    void waldInterval_bracketsTheProportion_andTightensWithVolume() {
        double[] ci = ExperimentStatistics.waldInterval(0.60d, 400L);
        assertTrue(ci[0] < 0.60d && ci[1] > 0.60d, "ci=[" + ci[0] + "," + ci[1] + "]");
        // half-width ~0.048 at n=400.
        assertEquals(0.60d - 0.048d, ci[0], 5e-3d);
        assertEquals(0.60d + 0.048d, ci[1], 5e-3d);
    }

    @Test
    void waldInterval_zeroVolume_isMaximallyWide() {
        double[] ci = ExperimentStatistics.waldInterval(0.5d, 0L);
        assertEquals(0.0d, ci[0], 1e-12d);
        assertEquals(1.0d, ci[1], 1e-12d);
    }

    @Test
    void waldInterval_isClampedToUnitInterval() {
        double[] ci = ExperimentStatistics.waldInterval(0.98d, 20L);
        assertTrue(ci[0] >= 0.0d);
        assertTrue(ci[1] <= 1.0d);
    }
}
