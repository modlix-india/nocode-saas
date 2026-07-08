package com.modlix.saas.adzump.service.creative;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.modlix.saas.adzump.model.Money;
import com.modlix.saas.adzump.model.leadzump.AdGrainId;
import com.modlix.saas.adzump.model.leadzump.Grain;
import com.modlix.saas.adzump.model.snapshot.CrmMetrics;
import com.modlix.saas.adzump.model.snapshot.PerformanceSnapshot;
import com.modlix.saas.adzump.model.snapshot.PlatformMetrics;
import com.modlix.saas.adzump.model.snapshot.SignalMaturity;
import com.modlix.saas.adzump.model.snapshot.SnapshotRow;
import com.modlix.saas.adzump.service.feedback.PolicyScorer;

/**
 * Offline unit tests for {@link CreativeScorer}: the creative-grain score is the spend-weighted rollup
 * (via the real J10 {@link PolicyScorer}) of the AD-grain rows joined to the creative, with volume, junk
 * rate, and the most-mature maturity carried so the loop does not judge a creative on thin data.
 */
class CreativeScorerTest {

    private final CreativeScorer scorer = new CreativeScorer(new PolicyScorer());

    private static SnapshotRow adRow(String adId, double blendedScore, String spend,
            Map<String, Long> counts, double junk, SignalMaturity maturity) {

        return new SnapshotRow()
                .setGrain(Grain.AD)
                .setAdGrainId(new AdGrainId().setAdId(adId))
                .setBlendedScore(blendedScore)
                .setPlatform(new PlatformMetrics().setSpend(new Money(new BigDecimal(spend), "INR")))
                .setCrm(counts == null ? null : new CrmMetrics().setCountByMilestone(counts).setJunkRate(junk))
                .setSignalMaturity(maturity);
    }

    @Test
    void score_rollsUpMatchedAdRows_carriesVolumeJunkAndMostMatureMaturity() {

        SnapshotRow r1 = adRow("cr1", 80.0, "1000", Map.of("lead", 20L, "qualified", 10L), 0.10, SignalMaturity.MATURE);
        SnapshotRow r2 = adRow("cr1", 40.0, "3000", Map.of("lead", 10L), 0.20, SignalMaturity.PARTIAL);
        SnapshotRow other = adRow("cr2", 100.0, "5000", Map.of("lead", 99L), 0.0, SignalMaturity.MATURE);

        PerformanceSnapshot snap = new PerformanceSnapshot().setGrainRows(List.of(r1, r2, other));

        CreativeScore score = this.scorer.score("cr1", snap);

        // rollup = (80*1000 + 40*3000) / 4000 = 50
        assertEquals(50.0d, score.score(), 1e-9);
        assertEquals(40L, score.volume());                       // 30 + 10 CRM outcomes
        assertEquals(0.125d, score.junkRate(), 1e-9);            // (0.10*30 + 0.20*10)/40
        assertEquals(SignalMaturity.MATURE, score.maturity());   // most-mature of the two rows
        assertTrue(score.judgeable());
        assertEquals(2, score.matchedAdRows());
    }

    @Test
    void score_ignoresNonAdGrainRowsAndOtherCreatives() {

        // A CAMPAIGN-grain row that happens to carry the same id must NOT be joined at the creative grain.
        SnapshotRow campaign = new SnapshotRow().setGrain(Grain.CAMPAIGN)
                .setAdGrainId(new AdGrainId().setAdId("cr1")).setBlendedScore(99.0);
        SnapshotRow ad = adRow("cr1", 70.0, "1000", Map.of("lead", 5L), 0.0, SignalMaturity.PARTIAL);

        CreativeScore score = this.scorer.score("cr1", new PerformanceSnapshot().setGrainRows(List.of(campaign, ad)));

        assertEquals(70.0d, score.score(), 1e-9);
        assertEquals(1, score.matchedAdRows());
        assertEquals(SignalMaturity.PARTIAL, score.maturity());
        assertFalse(score.judgeable());
    }

    @Test
    void score_empty_whenNoAdRowJoins() {

        SnapshotRow other = adRow("cr2", 90.0, "1000", Map.of("lead", 5L), 0.0, SignalMaturity.MATURE);
        CreativeScore score = this.scorer.score("cr1", new PerformanceSnapshot().setGrainRows(List.of(other)));

        assertEquals(0, score.matchedAdRows());
        assertEquals(0.0d, score.score(), 1e-9);
        assertEquals(0L, score.volume());
        assertFalse(score.judgeable());
        assertEquals(SignalMaturity.FAST_ONLY, score.maturity());
    }

    @Test
    void score_empty_onNullOrBlankInputs() {
        assertEquals(0, this.scorer.score(null, (PerformanceSnapshot) null).matchedAdRows());
        assertEquals(0, this.scorer.score("cr1", (PerformanceSnapshot) null).matchedAdRows());
    }
}
