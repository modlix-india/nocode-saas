package com.modlix.saas.adzump.service.optimize;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.modlix.saas.adzump.dto.AutonomyConfig;
import com.modlix.saas.adzump.dto.PerformancePolicy;
import com.modlix.saas.adzump.enums.CampaignType;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.enums.SpecialAdCategory;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.CampaignPlanBody;
import com.modlix.saas.adzump.model.Compliance;
import com.modlix.saas.adzump.model.Money;
import com.modlix.saas.adzump.model.Objective;
import com.modlix.saas.adzump.model.leadzump.AdGrainId;
import com.modlix.saas.adzump.model.leadzump.Grain;
import com.modlix.saas.adzump.model.snapshot.CrmMetrics;
import com.modlix.saas.adzump.model.snapshot.PerformanceSnapshot;
import com.modlix.saas.adzump.model.snapshot.PlatformMetrics;
import com.modlix.saas.adzump.model.snapshot.SignalMaturity;
import com.modlix.saas.adzump.model.snapshot.SnapshotRow;

/**
 * Seeded, offline fixtures for the J12 optimize tests: policies, plans, and hand-built
 * {@link PerformanceSnapshot}s with controlled per-grain scores and signal maturities. No live
 * platform/CRM — every number is set directly so the analyzers + gate are exercised deterministically.
 */
final class OptimizeFixtures {

    static final ObjectMapper MAPPER = new ObjectMapper();
    static final String INR = "INR";
    static final ULong PLAN_ID = ULong.valueOf(100);

    private OptimizeFixtures() {
    }

    // ---- policy / plan / autonomy ------------------------------------------------------------

    /** Full policy: platform + conversion + two leadzump stages + gates (minVolume 30, confidence 0.90). */
    static PerformancePolicy policy() {
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

        ObjectNode gates = body.putObject("gates");
        gates.put("minVolumePerVariant", 30);
        gates.put("confidence", 0.90);

        return new PerformancePolicy().setBody(body);
    }

    private static void stage(ArrayNode stages, String key, double targetCost, double weight, long minCount) {
        ObjectNode s = stages.addObject();
        s.put("stage", key);
        s.put("targetCost", targetCost);
        s.put("weight", weight);
        s.put("minCount", minCount);
    }

    /** An autonomy config carrying a max-changes-per-run cap (else no cap). */
    static AutonomyConfig autonomyWithMaxChanges(int maxChanges) {
        ObjectNode body = MAPPER.createObjectNode();
        ObjectNode caps = body.putObject("campaignChanges").putObject("caps");
        caps.put("maxChangesPerRun", maxChanges);
        caps.put("doNoHarm", true);
        caps.put("fastPauseSlowKill", true);
        return new AutonomyConfig().setBody(body);
    }

    static CampaignPlan plan(boolean googleSearch, boolean housing, Money targetCostPerOutcome) {
        CampaignPlanBody body = new CampaignPlanBody();

        if (housing)
            body.setCompliance(new Compliance().setSpecialAdCategory(SpecialAdCategory.HOUSING));

        if (targetCostPerOutcome != null)
            body.setObjective(new Objective().setTargetMilestone("qualified")
                    .setTargetCostPerOutcome(targetCostPerOutcome));

        CampaignPlan plan = new CampaignPlan().setClientCode("CLI0").setName("Plan")
                .setCampaignTypes(Map.of(Platform.GOOGLE, googleSearch ? CampaignType.SEARCH : CampaignType.DISPLAY));
        plan.setId(PLAN_ID);
        plan.setBody(body);
        return plan;
    }

    /** A plain Google-search plan (no housing, no bid target). */
    static CampaignPlan googlePlan() {
        return plan(true, false, null);
    }

    // ---- snapshot rows -----------------------------------------------------------------------

    static PerformanceSnapshot snapshot(double rollupScore, List<SnapshotRow> rows) {
        PerformanceSnapshot snap = new PerformanceSnapshot()
                .setCampaignPlanId(PLAN_ID)
                .setClientCode("CLI0")
                .setRollupScore(rollupScore)
                .setGrainRows(rows);
        snap.setId(ULong.valueOf(9));
        return snap;
    }

    static Money inr(double amount) {
        return new Money(BigDecimal.valueOf(amount), INR);
    }

    static AdGrainId campaignGrain(String campaignId) {
        return new AdGrainId().setCampaignId(campaignId);
    }

    static AdGrainId adSetGrain(String campaignId, String adSetId) {
        return new AdGrainId().setCampaignId(campaignId).setAdSetId(adSetId);
    }

    static AdGrainId adGrain(String campaignId, String adId) {
        return new AdGrainId().setCampaignId(campaignId).setAdId(adId);
    }

    /** A grain row with full control over platform metrics, CRM outcomes, maturity, and blended score. */
    static SnapshotRow row(Grain grain, AdGrainId id, long impressions, long clicks, double spend, double ctr,
            long platformConversions, Map<String, Long> outcomes, double junkRate, double blendedScore,
            SignalMaturity maturity) {

        PlatformMetrics platform = new PlatformMetrics()
                .setImpressions(impressions)
                .setClicks(clicks)
                .setSpend(inr(spend))
                .setCtr(ctr)
                .setPlatformConversions(platformConversions);

        CrmMetrics crm = null;
        if (outcomes != null) {
            crm = new CrmMetrics().setCountByMilestone(outcomes).setCostByMilestone(Map.of()).setJunkRate(junkRate);
        }

        return new SnapshotRow()
                .setGrain(grain)
                .setAdGrainId(id)
                .setPlatform(platform)
                .setCrm(crm)
                .setSignalMaturity(maturity)
                .setBlendedScore(blendedScore);
    }

    // ---- composed scenarios ------------------------------------------------------------------

    /**
     * A clear underperformer campaign (Google search): a winning ad set and a zero-outcome losing ad
     * set (same spend), plus a dead zero-outcome ad. Expect a budget shift off the loser, a negative
     * keyword on the wasteful ad group, and a pause of the dead ad.
     */
    static PerformanceSnapshot underperformer() {
        List<SnapshotRow> rows = new ArrayList<>();

        rows.add(row(Grain.CAMPAIGN, campaignGrain("C1"), 20000, 400, 16000, 0.02, 0,
                Map.of("lead", 25L, "qualified", 12L), 0.0, 40.0, SignalMaturity.MATURE));

        rows.add(row(Grain.ADSET, adSetGrain("C1", "AS_WIN"), 10000, 200, 8000, 0.04, 12,
                Map.of("lead", 25L, "qualified", 12L), 0.0, 80.0, SignalMaturity.MATURE));

        rows.add(row(Grain.ADSET, adSetGrain("C1", "AS_LOSE"), 10000, 200, 8000, 0.04, 0,
                null, 0.0, 0.0, SignalMaturity.FAST_ONLY));

        rows.add(row(Grain.AD, adGrain("C1", "AD_DEAD"), 3000, 120, 4000, 0.04, 0,
                null, 0.0, 0.0, SignalMaturity.FAST_ONLY));

        return snapshot(40.0, rows);
    }

    /** All-noise campaign: material score gap but every grain far below the min-volume gate. */
    static PerformanceSnapshot noise() {
        List<SnapshotRow> rows = new ArrayList<>();

        rows.add(row(Grain.CAMPAIGN, campaignGrain("C2"), 200, 10, 100, 0.05, 0,
                Map.of("lead", 2L), 0.0, 40.0, SignalMaturity.PARTIAL));

        rows.add(row(Grain.ADSET, adSetGrain("C2", "AS_A"), 120, 12, 60, 0.10, 1,
                Map.of("lead", 2L), 0.0, 45.0, SignalMaturity.PARTIAL));

        rows.add(row(Grain.ADSET, adSetGrain("C2", "AS_B"), 80, 8, 40, 0.10, 0,
                null, 0.0, 30.0, SignalMaturity.FAST_ONLY));

        rows.add(row(Grain.AD, adGrain("C2", "AD_x"), 60, 5, 30, 0.08, 0,
                null, 0.0, 0.0, SignalMaturity.FAST_ONLY));

        return snapshot(40.0, rows);
    }

    /**
     * A slow-converting potential winner on thin fast data: a low-score AD that HAS produced leads but
     * only PARTIAL maturity, alongside a second converting AD (so it is not the only converter). A kill
     * must be suppressed for immature signal.
     */
    static PerformanceSnapshot slowConverter() {
        List<SnapshotRow> rows = new ArrayList<>();

        rows.add(row(Grain.CAMPAIGN, campaignGrain("C3"), 8000, 300, 12000, 0.0375, 0,
                Map.of("lead", 25L, "qualified", 10L), 0.0, 45.0, SignalMaturity.PARTIAL));

        rows.add(row(Grain.AD, adGrain("C3", "AD_SLOW"), 4000, 150, 6000, 0.0375, 0,
                Map.of("lead", 5L), 0.0, 25.0, SignalMaturity.PARTIAL));

        rows.add(row(Grain.AD, adGrain("C3", "AD_OK"), 4000, 150, 6000, 0.0375, 10,
                Map.of("lead", 20L, "qualified", 10L), 0.0, 60.0, SignalMaturity.MATURE));

        return snapshot(45.0, rows);
    }
}
