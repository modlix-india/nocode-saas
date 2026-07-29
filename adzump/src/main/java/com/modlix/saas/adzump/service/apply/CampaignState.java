package com.modlix.saas.adzump.service.apply;

import java.time.LocalDateTime;
import java.util.Map;

import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.CampaignPlanBody;
import com.modlix.saas.adzump.model.Money;
import com.modlix.saas.adzump.model.leadzump.AdGrainId;
import com.modlix.saas.adzump.model.leadzump.Grain;
import com.modlix.saas.adzump.model.snapshot.PerformanceSnapshot;
import com.modlix.saas.adzump.model.snapshot.SignalMaturity;
import com.modlix.saas.adzump.model.snapshot.SnapshotRow;
import com.modlix.saas.adzump.service.optimize.AnalyzerContext;

/**
 * The live campaign facts the {@link GuardrailEngine} re-asserts against at apply time (J13 §5.2): the
 * current {@link CampaignPlan} (for the live daily budget + platforms), the latest {@link PerformanceSnapshot}
 * (for live spend, converter counts, per-grain signal maturity, and the snapshot's age), the current time,
 * and the last-applied timestamp per entity (for the rate/frequency guardrail). Immutable value object;
 * the metric math reuses {@link AnalyzerContext} so the do-no-harm / converter counting matches J12
 * exactly (one definition of "converter", "operating grain", "spend").
 */
public final class CampaignState {

    private final CampaignPlan plan;
    private final PerformanceSnapshot snapshot;
    private final LocalDateTime now;
    private final Map<String, LocalDateTime> lastAppliedByEntity;
    private final AnalyzerContext ctx;

    public CampaignState(CampaignPlan plan, PerformanceSnapshot snapshot, LocalDateTime now,
            Map<String, LocalDateTime> lastAppliedByEntity) {

        this.plan = plan;
        this.snapshot = snapshot;
        this.now = now == null ? LocalDateTime.now() : now;
        this.lastAppliedByEntity = lastAppliedByEntity == null ? Map.of() : lastAppliedByEntity;
        this.ctx = new AnalyzerContext(snapshot, plan, null); // null policy: metric helpers only
    }

    public CampaignPlan plan() {
        return this.plan;
    }

    public LocalDateTime now() {
        return this.now;
    }

    /** The snapshot the actions were diagnosed from — its {@code takenAt} drives the staleness guardrail. */
    public LocalDateTime snapshotTakenAt() {
        return this.snapshot == null ? null : this.snapshot.getTakenAt();
    }

    /** The campaign's current daily budget from the plan body, or {@code null} when unset. */
    public Money currentDailyBudget() {
        CampaignPlanBody body = this.plan == null ? null : this.plan.getBody();
        if (body == null || body.getBudget() == null)
            return null;
        return body.getBudget().getDailyBudget();
    }

    /** The live snapshot row for a target grain (matched on the most specific id it carries), or null. */
    public SnapshotRow rowFor(AdGrainId target) {
        if (this.snapshot == null || this.snapshot.getGrainRows() == null || target == null)
            return null;
        for (SnapshotRow row : this.snapshot.getGrainRows())
            if (row != null && matches(row.getAdGrainId(), target))
                return row;
        return null;
    }

    /** The target row's CRM signal maturity, defaulting to {@code FAST_ONLY} when unknown. */
    public SignalMaturity maturityFor(AdGrainId target) {
        SnapshotRow row = rowFor(target);
        return row != null && row.getSignalMaturity() != null ? row.getSignalMaturity() : SignalMaturity.FAST_ONLY;
    }

    /** The min-volume signal (clicks) for the target row — surfaced to the {@link SignificanceGate} re-check. */
    public long volumeFor(AdGrainId target) {
        return AnalyzerContext.clicks(rowFor(target));
    }

    /** {@code true} when the target grain has produced a CRM outcome (a "converter"). */
    public boolean isConverter(AdGrainId target) {
        return AnalyzerContext.isConverter(rowFor(target));
    }

    /**
     * {@code true} when the target is the only grain (at its own grain level) still producing outcomes —
     * do-no-harm never zeroes it.
     */
    public boolean isOnlyConverter(AdGrainId target) {
        SnapshotRow row = rowFor(target);
        if (row == null || !AnalyzerContext.isConverter(row))
            return false;
        return this.ctx.converterCountAt(row.getGrain()) <= 1L;
    }

    /**
     * How many units at the operating grain are still active (have live impressions). Used by the
     * "never leave a campaign with zero active ads" guardrail: pausing the target must not drop this to
     * zero (i.e. the target must not be the last active unit).
     */
    public long activeUnitCount() {
        long n = 0L;
        for (SnapshotRow row : this.ctx.rowsAt(this.ctx.operatingGrain()))
            if (AnalyzerContext.impressions(row) > 0L)
                n++;
        return n;
    }

    /** The last time this entity was changed by an applied action, or {@code null} when never. */
    public LocalDateTime lastAppliedAt(AdGrainId target) {
        return this.lastAppliedByEntity.get(grainKey(target));
    }

    // ---- grain identity ----------------------------------------------------------------------

    private static boolean matches(AdGrainId rowId, AdGrainId target) {
        if (rowId == null)
            return false;
        if (target.getAdId() != null)
            return target.getAdId().equals(rowId.getAdId());
        if (target.getAdSetId() != null)
            return target.getAdSetId().equals(rowId.getAdSetId());
        if (target.getCampaignId() != null)
            return target.getCampaignId().equals(rowId.getCampaignId());
        return false;
    }

    /** A stable, most-specific key for an ad-grain target (used to index applied history). */
    public static String grainKey(AdGrainId target) {
        if (target == null)
            return "";
        if (target.getAdId() != null)
            return "ad:" + target.getAdId();
        if (target.getAdSetId() != null)
            return "adset:" + target.getAdSetId();
        if (target.getCampaignId() != null)
            return "campaign:" + target.getCampaignId();
        return "";
    }
}
