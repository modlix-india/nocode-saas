package com.modlix.saas.adzump.service.apply;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.modlix.saas.adzump.dto.AutonomyConfig;
import com.modlix.saas.adzump.enums.CampaignType;
import com.modlix.saas.adzump.enums.MatchType;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditActionType;
import com.modlix.saas.adzump.model.BudgetPlan;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.CampaignPlanBody;
import com.modlix.saas.adzump.model.Money;
import com.modlix.saas.adzump.model.leadzump.AdGrainId;
import com.modlix.saas.adzump.model.leadzump.Grain;
import com.modlix.saas.adzump.model.snapshot.CrmMetrics;
import com.modlix.saas.adzump.model.snapshot.PerformanceSnapshot;
import com.modlix.saas.adzump.model.snapshot.PlatformMetrics;
import com.modlix.saas.adzump.model.snapshot.SignalMaturity;
import com.modlix.saas.adzump.model.snapshot.SnapshotRow;
import com.modlix.saas.adzump.model.snapshot.SnapshotWindow;
import com.modlix.saas.adzump.service.optimize.Action;
import com.modlix.saas.adzump.service.optimize.ActionChange;
import com.modlix.saas.adzump.service.optimize.ActionSet;
import com.modlix.saas.adzump.service.optimize.Risk;
import com.modlix.saas.adzump.service.optimize.SignificanceVerdict;

/**
 * Seeded, offline fixtures for the J13 apply tests: {@link Action}s of each type + risk, {@link CampaignPlan}s
 * with a known daily budget, hand-built {@link PerformanceSnapshot}s (converter / non-converter rows,
 * controlled maturity), and {@link AutonomyConfig}s per mode with tunable caps. No live platform / CRM /
 * DB — every number is set directly so the router + guardrails are exercised deterministically.
 */
final class ApplyFixtures {

    static final ObjectMapper MAPPER = new ObjectMapper();
    static final String USD = "USD";
    static final String CLIENT = "CLI0";
    static final ULong PLAN_ID = ULong.valueOf(100);
    static final ULong SNAPSHOT_ID = ULong.valueOf(9);

    private ApplyFixtures() {
    }

    // ---- money / grains ----------------------------------------------------------------------

    static Money usd(double amount) {
        return new Money(BigDecimal.valueOf(amount), USD);
    }

    static AdGrainId adGrain(String campaignId, String adId) {
        return new AdGrainId().setCampaignId(campaignId).setAdId(adId);
    }

    static AdGrainId adSetGrain(String campaignId, String adSetId) {
        return new AdGrainId().setCampaignId(campaignId).setAdSetId(adSetId);
    }

    // ---- actions -----------------------------------------------------------------------------

    static Action action(AdzumpActionAuditActionType type, AdGrainId target, ActionChange change, Risk risk) {
        SignificanceVerdict verdict = SignificanceVerdict.passed(100.0d, 0.90d, "seeded");
        return new Action(type, target, change, "seeded rationale", 5.0d, 0.8d, verdict, risk, true);
    }

    static Action negativeKeyword(AdGrainId target, Risk risk) {
        return action(AdzumpActionAuditActionType.ADD_NEGATIVE_KEYWORD, target,
                new ActionChange.NegativeKeyword(MatchType.EXACT, List.of("cheap"), "wasteful"), risk);
    }

    static Action budgetShift(AdGrainId from, AdGrainId to, double amount, double pctOfSource, Risk risk) {
        return action(AdzumpActionAuditActionType.SHIFT_BUDGET, to,
                new ActionChange.BudgetShift(from, to, usd(amount), pctOfSource), risk);
    }

    static Action pause(AdGrainId target, boolean kill, Risk risk) {
        return action(AdzumpActionAuditActionType.PAUSE_ENTITY, target,
                new ActionChange.Pause(kill, kill ? "kill converter" : "zero-outcome loser"), risk);
    }

    static Action bidChange(AdGrainId target, Risk risk) {
        return action(AdzumpActionAuditActionType.ADJUST_BID, target,
                new ActionChange.BidChange("LOWER", usd(400), usd(300), "hint"), risk);
    }

    static Action refineAudience(AdGrainId target, Risk risk) {
        return action(AdzumpActionAuditActionType.REFINE_AUDIENCE, target,
                new ActionChange.AudienceRefinement("NARROW", "interest", "detail"), risk);
    }

    static Action rotateCreative(AdGrainId target, Risk risk) {
        return action(AdzumpActionAuditActionType.ROTATE_CREATIVE, target,
                new ActionChange.CreativeRotation("cr_1", "fresh"), risk);
    }

    static Action requestVariant(AdGrainId target, Risk risk) {
        return action(AdzumpActionAuditActionType.REQUEST_VARIANT, target,
                new ActionChange.VariantRequest("cr_win", Map.of()), risk);
    }

    static ActionSet actionSet(List<Action> actions) {
        return new ActionSet(PLAN_ID, SNAPSHOT_ID, LocalDateTime.now(), actions, List.of(), 40.0d, 50.0d);
    }

    // ---- plan --------------------------------------------------------------------------------

    static CampaignPlan plan(double dailyBudget) {
        CampaignPlanBody body = new CampaignPlanBody()
                .setBudget(new BudgetPlan().setCurrency(USD).setDailyBudget(usd(dailyBudget)));
        CampaignPlan plan = new CampaignPlan()
                .setClientCode(CLIENT)
                .setName("Plan")
                .setProductId("prd_1")
                .setVertical("real_estate")
                .setCampaignTypes(Map.of(Platform.GOOGLE, CampaignType.SEARCH));
        plan.setId(PLAN_ID);
        plan.setBody(body);
        return plan;
    }

    // ---- snapshot ----------------------------------------------------------------------------

    static SnapshotRow row(Grain grain, AdGrainId id, long impressions, long clicks, Map<String, Long> outcomes,
            SignalMaturity maturity) {

        PlatformMetrics platform = new PlatformMetrics()
                .setImpressions(impressions)
                .setClicks(clicks)
                .setSpend(usd(100))
                .setCtr(0.03d)
                .setPlatformConversions(0);

        CrmMetrics crm = null;
        if (outcomes != null)
            crm = new CrmMetrics().setCountByMilestone(outcomes).setCostByMilestone(Map.of()).setJunkRate(0.0d);

        return new SnapshotRow()
                .setGrain(grain)
                .setAdGrainId(id)
                .setPlatform(platform)
                .setCrm(crm)
                .setSignalMaturity(maturity)
                .setBlendedScore(30.0d);
    }

    static PerformanceSnapshot snapshot(LocalDateTime takenAt, List<SnapshotRow> rows) {
        PerformanceSnapshot snap = new PerformanceSnapshot()
                .setCampaignPlanId(PLAN_ID)
                .setClientCode(CLIENT)
                .setWindow(new SnapshotWindow().setFrom(LocalDateTime.now().minusDays(7).toLocalDate())
                        .setTo(LocalDateTime.now().toLocalDate()).setTimezone("Asia/Kolkata"))
                .setTakenAt(takenAt)
                .setRollupScore(40.0d)
                .setGrainRows(rows);
        snap.setId(SNAPSHOT_ID);
        return snap;
    }

    /** A snapshot with a single non-converting AD (the target), taken now. */
    static PerformanceSnapshot loneNonConverterSnapshot(AdGrainId target) {
        List<SnapshotRow> rows = new ArrayList<>();
        rows.add(row(Grain.AD, target, 3000, 120, null, SignalMaturity.FAST_ONLY));
        return snapshot(LocalDateTime.now(), rows);
    }

    // ---- autonomy config ---------------------------------------------------------------------

    /**
     * An autonomy config for {@code mode} with the given caps. {@code maxChangePct}/{@code dailyCap}/
     * {@code staleHours}/{@code minHours} are only written when positive (else the cap is unset =
     * disabled).
     */
    static AutonomyConfig autonomy(String mode, double maxChangePct, Double dailyCap, long staleHours,
            long minHours) {

        ObjectNode body = MAPPER.createObjectNode();
        ObjectNode campaignChanges = body.putObject("campaignChanges");
        campaignChanges.put("mode", mode);
        ObjectNode caps = campaignChanges.putObject("caps");
        caps.put("doNoHarm", true);
        caps.put("fastPauseSlowKill", true);
        if (maxChangePct > 0.0d)
            caps.put("maxBudgetChangePctPerRun", maxChangePct);
        if (dailyCap != null) {
            ObjectNode cap = caps.putObject("dailyBudgetCap");
            cap.put("amount", dailyCap);
            cap.put("currency", USD);
        }
        if (staleHours > 0L)
            caps.put("staleSnapshotMaxAgeHours", staleHours);
        if (minHours > 0L)
            caps.put("minHoursBetweenChangesPerEntity", minHours);

        return new AutonomyConfig().setClientCode(CLIENT).setBody(body);
    }

    static AutonomyConfig hybrid(double maxChangePct) {
        return autonomy("HYBRID", maxChangePct, null, 0L, 0L);
    }

    static AutonomyConfig autonomous(double maxChangePct) {
        return autonomy("AUTONOMOUS", maxChangePct, null, 0L, 0L);
    }
}
