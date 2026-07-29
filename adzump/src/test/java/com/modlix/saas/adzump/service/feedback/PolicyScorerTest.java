package com.modlix.saas.adzump.service.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.modlix.saas.adzump.dto.PerformancePolicy;
import com.modlix.saas.adzump.model.Money;
import com.modlix.saas.adzump.model.leadzump.Grain;
import com.modlix.saas.adzump.model.snapshot.CrmMetrics;
import com.modlix.saas.adzump.model.snapshot.PlatformMetrics;
import com.modlix.saas.adzump.model.snapshot.SignalMaturity;
import com.modlix.saas.adzump.model.snapshot.SnapshotRow;

/**
 * Offline unit tests for the deterministic, config-driven blended {@link PolicyScorer} (J10 §5.4) —
 * the score is checked against a hand-computed {@link PerformancePolicy}, the volume gates are shown
 * to drop layers (the fast&rarr;slow handoff), maturity classification is exercised, and the rollup
 * is verified spend-weighted over the coarsest grain.
 */
class PolicyScorerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String INR = "INR";

    private final PolicyScorer scorer = new PolicyScorer();

    // A full policy: platform + conversion + two leadzump stages + junk penalty.
    private static PerformancePolicy fullPolicy() {

        ObjectNode body = MAPPER.createObjectNode();

        ObjectNode platform = body.putObject("platform");
        platform.put("weight", 0.20);
        platform.put("targetCtr", 0.02);
        platform.put("minImpressions", 1000);

        ObjectNode conversion = body.putObject("conversion");
        conversion.put("weight", 0.30);
        conversion.put("targetCpl", 400);
        conversion.put("minClicks", 50);

        ArrayNode stages = body.putArray("leadzumpStages");
        stage(stages, "lead", 400, 0.10, 20);
        stage(stages, "qualified", 800, 0.40, 10);

        body.put("junkPenalty", 0.5);

        return new PerformancePolicy().setBody(body);
    }

    private static void stage(ArrayNode stages, String key, double targetCost, double weight, long minCount) {
        ObjectNode s = stages.addObject();
        s.put("stage", key);
        s.put("targetCost", targetCost);
        s.put("weight", weight);
        s.put("minCount", minCount);
    }

    private static PlatformMetrics platform(long impressions, long clicks, double ctr, String spend) {
        return new PlatformMetrics()
                .setImpressions(impressions)
                .setClicks(clicks)
                .setCtr(ctr)
                .setSpend(new Money(new BigDecimal(spend), INR));
    }

    private static Money inr(String amount) {
        return new Money(new BigDecimal(amount), INR);
    }

    // =====================================================================================

    @Test
    void score_matchesHandComputedBlend_allLayersGatedIn() {

        // impressions 1000 (== gate), clicks 50 (== gate), ctr 0.05, spend 10000.
        SnapshotRow row = new SnapshotRow()
                .setGrain(Grain.AD)
                .setPlatform(platform(1000, 50, 0.05, "10000"))
                .setCrm(new CrmMetrics()
                        .setCountByMilestone(Map.of("lead", 40L, "qualified", 20L))
                        .setCostByMilestone(Map.of("lead", inr("300"), "qualified", inr("1000")))
                        .setJunkRate(0.10));

        // platform:    clamp(0.05/0.02)=1.0 -> 100 (w 0.20)
        // conversion:  actualCpl = 10000/40 = 250; clamp(400/250)=1.0 -> 100 (w 0.30)
        // lead stage:  clamp(400/300)=1.0 -> 100 (w 0.10)
        // qualified:   clamp(800/1000)=0.8 -> 80 (w 0.40)
        // blended = (100*.20 + 100*.30 + 100*.10 + 80*.40) / 1.00 = 92
        // junk    = 92 * (1 - 0.5*0.10) = 92 * 0.95 = 87.4
        double score = this.scorer.score(row, fullPolicy());

        assertEquals(87.4d, score, 1e-6);
    }

    @Test
    void score_volumeGatesDropUnmaturedLayers_fastToSlowHandoff() {

        // platform gate NOT met (500 < 1000), conversion gate NOT met (10 < 50),
        // lead stage met (25 >= 20), qualified NOT met (3 < 10) -> only the lead stage scores.
        SnapshotRow row = new SnapshotRow()
                .setGrain(Grain.AD)
                .setPlatform(platform(500, 10, 0.02, "4000"))
                .setCrm(new CrmMetrics()
                        .setCountByMilestone(Map.of("lead", 25L, "qualified", 3L))
                        .setCostByMilestone(Map.of("lead", inr("200")))
                        .setJunkRate(0.0));

        // only lead: clamp(400/200)=1.0 -> 100; single layer -> blended 100; no junk.
        double score = this.scorer.score(row, fullPolicy());

        assertEquals(100.0d, score, 1e-6);
    }

    @Test
    void score_zero_whenNoLayerGateIsMet() {

        SnapshotRow row = new SnapshotRow()
                .setGrain(Grain.AD)
                .setPlatform(platform(500, 10, 0.01, "1000"))
                .setCrm(new CrmMetrics()
                        .setCountByMilestone(Map.of("lead", 5L, "qualified", 1L))
                        .setCostByMilestone(Map.of("lead", inr("200")))
                        .setJunkRate(0.0));

        assertEquals(0.0d, this.scorer.score(row, fullPolicy()), 1e-9);
    }

    // =====================================================================================
    // Maturity
    // =====================================================================================

    @Test
    void classifyMaturity_fastOnly_whenNoCrmJoined() {

        SnapshotRow platformOnly = new SnapshotRow().setGrain(Grain.AD).setPlatform(platform(1000, 50, 0.05, "10000"));
        assertEquals(SignalMaturity.FAST_ONLY, this.scorer.classifyMaturity(platformOnly, fullPolicy()));

        SnapshotRow emptyCrm = new SnapshotRow().setGrain(Grain.AD).setPlatform(platform(1000, 50, 0.05, "10000"))
                .setCrm(new CrmMetrics().setCountByMilestone(Map.of("lead", 0L)));
        assertEquals(SignalMaturity.FAST_ONLY, this.scorer.classifyMaturity(emptyCrm, fullPolicy()));
    }

    @Test
    void classifyMaturity_partial_whenLeadsButNoDownstreamGateMet() {

        // entry leads present, no downstream at all
        SnapshotRow noDownstream = new SnapshotRow().setGrain(Grain.AD)
                .setCrm(new CrmMetrics().setCountByMilestone(Map.of("lead", 30L, "qualified", 0L)));
        assertEquals(SignalMaturity.PARTIAL, this.scorer.classifyMaturity(noDownstream, fullPolicy()));

        // downstream present but below its minCount (5 < 10)
        SnapshotRow thinDownstream = new SnapshotRow().setGrain(Grain.AD)
                .setCrm(new CrmMetrics().setCountByMilestone(Map.of("lead", 30L, "qualified", 5L)));
        assertEquals(SignalMaturity.PARTIAL, this.scorer.classifyMaturity(thinDownstream, fullPolicy()));
    }

    @Test
    void classifyMaturity_mature_whenDownstreamGateMet() {

        SnapshotRow mature = new SnapshotRow().setGrain(Grain.AD)
                .setCrm(new CrmMetrics().setCountByMilestone(Map.of("lead", 30L, "qualified", 15L)));
        assertEquals(SignalMaturity.MATURE, this.scorer.classifyMaturity(mature, fullPolicy()));
    }

    // =====================================================================================
    // Rollup
    // =====================================================================================

    @Test
    void rollup_spendWeighted_overCoarsestGrainOnly() {

        SnapshotRow campA = new SnapshotRow().setGrain(Grain.CAMPAIGN).setBlendedScore(80.0)
                .setPlatform(platform(0, 0, 0, "1000"));
        SnapshotRow campB = new SnapshotRow().setGrain(Grain.CAMPAIGN).setBlendedScore(40.0)
                .setPlatform(platform(0, 0, 0, "3000"));
        // A finer-grain row must be ignored by the rollup (avoids double-counting spend).
        SnapshotRow ad = new SnapshotRow().setGrain(Grain.AD).setBlendedScore(100.0)
                .setPlatform(platform(0, 0, 0, "500"));

        // (80*1000 + 40*3000) / 4000 = 200000/4000 = 50
        assertEquals(50.0d, this.scorer.rollup(List.of(campA, campB, ad)), 1e-9);
    }

    @Test
    void rollup_arithmeticMean_whenNoSpend() {

        SnapshotRow campA = new SnapshotRow().setGrain(Grain.CAMPAIGN).setBlendedScore(60.0)
                .setPlatform(platform(0, 0, 0, "0"));
        SnapshotRow campB = new SnapshotRow().setGrain(Grain.CAMPAIGN).setBlendedScore(80.0)
                .setPlatform(platform(0, 0, 0, "0"));

        assertEquals(70.0d, this.scorer.rollup(List.of(campA, campB)), 1e-9);
    }
}
