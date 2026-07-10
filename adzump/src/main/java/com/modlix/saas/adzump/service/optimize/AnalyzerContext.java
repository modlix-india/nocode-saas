package com.modlix.saas.adzump.service.optimize;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.modlix.saas.adzump.dto.PerformancePolicy;
import com.modlix.saas.adzump.enums.CampaignType;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.enums.SpecialAdCategory;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.CampaignPlanBody;
import com.modlix.saas.adzump.model.Money;
import com.modlix.saas.adzump.model.Objective;
import com.modlix.saas.adzump.model.leadzump.Grain;
import com.modlix.saas.adzump.model.snapshot.CrmMetrics;
import com.modlix.saas.adzump.model.snapshot.PerformanceSnapshot;
import com.modlix.saas.adzump.model.snapshot.PlatformMetrics;
import com.modlix.saas.adzump.model.snapshot.SnapshotRow;

/**
 * Read-only view the {@link DimensionAnalyzer}s share for one optimization run: the
 * {@link PerformanceSnapshot} (J10 facts), the {@link CampaignPlan} (targets / caps / compliance /
 * creatives), and the effective {@link PerformancePolicy}, plus the derived helpers every analyzer
 * would otherwise re-implement (grain slicing, spend, outcome counts, campaign baselines, converter
 * counting). Built once by the engine and passed to each analyzer; holds no mutable state.
 */
public final class AnalyzerContext {

    private final PerformanceSnapshot snapshot;
    private final CampaignPlan plan;
    private final PerformancePolicy policy;

    private final boolean housing;
    private final boolean googleSearch;
    private final String targetMilestone;
    private final String entryStage;

    public AnalyzerContext(PerformanceSnapshot snapshot, CampaignPlan plan, PerformancePolicy policy) {
        this.snapshot = snapshot;
        this.plan = plan;
        this.policy = policy;
        this.housing = resolveHousing(plan);
        this.googleSearch = resolveGoogleSearch(plan);
        this.targetMilestone = resolveTargetMilestone(plan);
        this.entryStage = resolveEntryStage(policy);
    }

    // ---- the raw inputs ----------------------------------------------------------------------

    public PerformanceSnapshot snapshot() {
        return this.snapshot;
    }

    public CampaignPlan plan() {
        return this.plan;
    }

    public PerformancePolicy policy() {
        return this.policy;
    }

    // ---- plan-derived flags ------------------------------------------------------------------

    /** HOUSING (etc.) special ad category locks demographic targeting — audience refinement must avoid it. */
    public boolean isHousing() {
        return this.housing;
    }

    /** The plan runs a Google search-family type (SEARCH/DSA/SHOPPING) — the only place negative keywords apply. */
    public boolean isGoogleSearch() {
        return this.googleSearch;
    }

    /** The milestone the campaign optimizes toward (e.g. {@code site_visit}); may be null. */
    public String targetMilestone() {
        return this.targetMilestone;
    }

    /** The entry (first) leadzump stage from the policy (e.g. {@code lead}); may be null. */
    public String entryStage() {
        return this.entryStage;
    }

    // ---- grain slicing -----------------------------------------------------------------------

    public List<SnapshotRow> rowsAt(Grain grain) {
        List<SnapshotRow> out = new ArrayList<>();
        if (this.snapshot == null || this.snapshot.getGrainRows() == null)
            return out;
        for (SnapshotRow r : this.snapshot.getGrainRows())
            if (r != null && r.getGrain() == grain)
                out.add(r);
        return out;
    }

    /**
     * The grain budget / audience / keyword actions operate on: the ad-set / ad-group level when the
     * snapshot has ADSET rows, else the AD level. (Budget is allocated at the ad-set/ad-group grain.)
     */
    public Grain operatingGrain() {
        return rowsAt(Grain.ADSET).isEmpty() ? Grain.AD : Grain.ADSET;
    }

    // ---- metrics per row ---------------------------------------------------------------------

    public static long clicks(SnapshotRow row) {
        PlatformMetrics p = row == null ? null : row.getPlatform();
        return p == null ? 0L : p.getClicks();
    }

    public static long impressions(SnapshotRow row) {
        PlatformMetrics p = row == null ? null : row.getPlatform();
        return p == null ? 0L : p.getImpressions();
    }

    public static long platformConversions(SnapshotRow row) {
        PlatformMetrics p = row == null ? null : row.getPlatform();
        return p == null ? 0L : p.getPlatformConversions();
    }

    public static double ctr(SnapshotRow row) {
        PlatformMetrics p = row == null ? null : row.getPlatform();
        return p == null ? 0.0d : p.getCtr();
    }

    public static double spend(SnapshotRow row) {
        PlatformMetrics p = row == null ? null : row.getPlatform();
        Money m = p == null ? null : p.getSpend();
        return amount(m);
    }

    public static String currency(SnapshotRow row) {
        PlatformMetrics p = row == null ? null : row.getPlatform();
        Money m = p == null ? null : p.getSpend();
        return m == null ? null : m.getCurrency();
    }

    public static double junkRate(SnapshotRow row) {
        CrmMetrics c = row == null ? null : row.getCrm();
        return c == null ? 0.0d : c.getJunkRate();
    }

    /** Total CRM outcomes across all milestones — the "has this grain produced anything?" signal. */
    public static long totalOutcomes(SnapshotRow row) {
        CrmMetrics c = row == null ? null : row.getCrm();
        if (c == null || c.getCountByMilestone() == null)
            return 0L;
        long total = 0L;
        for (Long v : c.getCountByMilestone().values())
            total += v == null ? 0L : v;
        return total;
    }

    /** The count at a specific milestone key (case-insensitive), or 0. */
    public static long milestoneCount(SnapshotRow row, String milestone) {
        CrmMetrics c = row == null ? null : row.getCrm();
        if (c == null || c.getCountByMilestone() == null || milestone == null)
            return 0L;
        for (Map.Entry<String, Long> e : c.getCountByMilestone().entrySet())
            if (milestone.equalsIgnoreCase(e.getKey()))
                return e.getValue() == null ? 0L : e.getValue();
        return 0L;
    }

    /** A grain is a "converter" once it has produced any CRM outcome. */
    public static boolean isConverter(SnapshotRow row) {
        return totalOutcomes(row) > 0L;
    }

    /** Pure waste: spend landed, but no CRM outcome and no platform-attributed conversion. */
    public static boolean isPureWaste(SnapshotRow row) {
        return spend(row) > 0.0d && totalOutcomes(row) == 0L && platformConversions(row) == 0L;
    }

    // ---- campaign aggregates -----------------------------------------------------------------

    /**
     * The campaign's total window spend for share math: the CAMPAIGN-grain spend when present (the
     * authoritative total), else the sum over the operating grain.
     */
    public double campaignSpend() {
        double campaign = 0.0d;
        for (SnapshotRow r : rowsAt(Grain.CAMPAIGN))
            campaign += spend(r);
        if (campaign > 0.0d)
            return campaign;
        double sum = 0.0d;
        for (SnapshotRow r : rowsAt(operatingGrain()))
            sum += spend(r);
        return sum;
    }

    /** The blended objective now — the snapshot's rolled-up score (J10 §5.4). */
    public double rollupScore() {
        return this.snapshot == null ? 0.0d : this.snapshot.getRollupScore();
    }

    /** How many rows at a grain are still producing outcomes (do-no-harm counts these). */
    public long converterCountAt(Grain grain) {
        long n = 0L;
        for (SnapshotRow r : rowsAt(grain))
            if (isConverter(r))
                n++;
        return n;
    }

    // ---- resolution --------------------------------------------------------------------------

    private static double amount(Money m) {
        return m == null || m.getAmount() == null ? 0.0d : m.getAmount().doubleValue();
    }

    private static boolean resolveHousing(CampaignPlan plan) {
        CampaignPlanBody body = plan == null ? null : plan.getBody();
        if (body == null || body.getCompliance() == null)
            return false;
        SpecialAdCategory cat = body.getCompliance().getSpecialAdCategory();
        return cat != null && cat != SpecialAdCategory.NONE;
    }

    private static boolean resolveGoogleSearch(CampaignPlan plan) {
        if (plan == null || plan.getCampaignTypes() == null)
            return false;
        CampaignType google = plan.getCampaignTypes().get(Platform.GOOGLE);
        return google == CampaignType.SEARCH || google == CampaignType.DSA || google == CampaignType.SHOPPING;
    }

    private static String resolveTargetMilestone(CampaignPlan plan) {
        CampaignPlanBody body = plan == null ? null : plan.getBody();
        Objective obj = body == null ? null : body.getObjective();
        return obj == null ? null : obj.getTargetMilestone();
    }

    private static String resolveEntryStage(PerformancePolicy policy) {
        JsonNode body = policy == null ? null : policy.getBody();
        if (body == null)
            return null;
        JsonNode stages = body.get("leadzumpStages");
        if (stages == null || !stages.isArray() || stages.isEmpty())
            return null;
        JsonNode first = stages.get(0);
        String stage = first == null ? null : first.path("stage").asText(null);
        return stage == null || stage.isBlank() ? null : stage;
    }
}
