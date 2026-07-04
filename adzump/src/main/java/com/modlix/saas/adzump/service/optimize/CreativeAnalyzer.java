package com.modlix.saas.adzump.service.optimize;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditActionType;
import com.modlix.saas.adzump.model.CampaignPlanBody;
import com.modlix.saas.adzump.model.Creative;
import com.modlix.saas.adzump.model.leadzump.Grain;
import com.modlix.saas.adzump.model.snapshot.SnapshotRow;
import com.modlix.saas.adzump.service.optimize.CreativeScoreProvider.CreativeSignal;

/**
 * Creative dimension (J12 §5.2): rotate / pause low-outcome creatives and request new variants from
 * winners, <b>consulting J20 creative scores</b> via the {@link CreativeScoreProvider} seam. Operates
 * at the AD grain. Four cases, gated for the J10 fast/slow discipline (the {@link SignificanceGate}
 * enforces min-volume and — for kills — MATURE signal):
 * <ul>
 * <li><b>pure waste</b> (spend, no CRM outcome, no platform conversion) → {@code PAUSE_ENTITY} as a
 * low-risk trim ({@code kill = false}; allowed on fast signal) — "pause the zero-outcome ad";</li>
 * <li><b>confirmed low-score converter</b> (score &lt; {@link #LOW_SCORE}) → {@code PAUSE_ENTITY} as a
 * kill ({@code kill = true}) — proposed liberally, but the gate suppresses it unless the CRM signal is
 * MATURE and it is not the only converter (so a slow-converting potential winner on thin data is never
 * killed);</li>
 * <li><b>mediocre converter</b> ({@link #LOW_SCORE}..{@link #MID_SCORE}) → {@code ROTATE_CREATIVE}
 * (refresh);</li>
 * <li><b>winner</b> (J20 winner flag) → {@code REQUEST_VARIANT} to exploit the winning attributes.</li>
 * </ul>
 */
@Service
public class CreativeAnalyzer implements DimensionAnalyzer {

    /** Below this blended score a converting creative is a confirmed underperformer (kill candidate). */
    static final double LOW_SCORE = 30.0d;

    /** Between LOW_SCORE and this, a creative is mediocre — rotate/refresh rather than kill. */
    static final double MID_SCORE = 50.0d;

    private final CreativeScoreProvider creativeScoreProvider;

    public CreativeAnalyzer(CreativeScoreProvider creativeScoreProvider) {
        this.creativeScoreProvider = creativeScoreProvider;
    }

    @Override
    public List<Candidate> analyze(AnalyzerContext ctx) {

        List<Candidate> out = new ArrayList<>();
        List<SnapshotRow> adRows = ctx.rowsAt(Grain.AD);
        if (adRows.isEmpty())
            return out;

        double campaignSpend = ctx.campaignSpend();
        double rollup = ctx.rollupScore();
        long converterCount = ctx.converterCountAt(Grain.AD);

        for (SnapshotRow row : adRows) {

            CreativeSignal signal = this.creativeScoreProvider.signalFor(row, ctx);
            double score = signal.blendedScore();
            long clicks = AnalyzerContext.clicks(row);
            double spendShare = campaignSpend > 0.0d ? AnalyzerContext.spend(row) / campaignSpend : 0.0d;
            String creativeId = resolveCreativeId(ctx);

            // 1. Pure waste: pause as a low-risk trim (not a kill of a converter).
            if (AnalyzerContext.isPureWaste(row)) {
                double delta = rollup * spendShare; // freed waste redeployed at campaign efficiency
                ActionChange change = new ActionChange.Pause(false,
                        "spend with no CRM outcome and no platform conversion");
                String rationale = String.format(
                        "Pause the ad: %.0f spent over %d clicks with zero outcomes — obvious waste.",
                        AnalyzerContext.spend(row), clicks);
                out.add(new Candidate(AdzumpActionAuditActionType.PAUSE_ENTITY, row.getAdGrainId(), change,
                        rationale, delta, 0.85d, Risk.MED, false, false, row.getSignalMaturity(),
                        clicks, 0L, 0L, 0L, 0L));
                continue;
            }

            boolean converter = AnalyzerContext.isConverter(row);

            // 2. Confirmed low-score converter: propose a kill; the gate decides on maturity + do-no-harm.
            if (converter && score < LOW_SCORE) {
                double delta = Math.max(0.0d, rollup - score) * spendShare;
                boolean onlyConverter = converterCount <= 1;
                ActionChange change = new ActionChange.Pause(true,
                        String.format("converting but scoring %.1f — a confirmed underperformer", score));
                String rationale = String.format(
                        "Pause the ad: it converts but scores only %.1f (well below the %.0f floor).",
                        score, LOW_SCORE);
                out.add(new Candidate(AdzumpActionAuditActionType.PAUSE_ENTITY, row.getAdGrainId(), change,
                        rationale, delta, 0.75d, Risk.HIGH, true, onlyConverter, row.getSignalMaturity(),
                        clicks, 0L, 0L, 0L, 0L));
                continue;
            }

            // 3. Mediocre converter: rotate the creative (a low-risk refresh).
            if (converter && score < MID_SCORE) {
                double delta = Math.min(4.0d, spendShare * 5.0d);
                ActionChange change = new ActionChange.CreativeRotation(creativeId, "refresh a mediocre creative");
                String rationale = String.format(
                        "Rotate the creative: mid-pack score %.1f — refresh to lift performance.", score);
                out.add(new Candidate(AdzumpActionAuditActionType.ROTATE_CREATIVE, row.getAdGrainId(), change,
                        rationale, delta, 0.60d, Risk.LOW, false, false, row.getSignalMaturity(),
                        clicks, 0L, 0L, 0L, 0L));
                continue;
            }

            // 4. Winner: request new variants to exploit the winning attributes (J20-informed).
            if (signal.winner()) {
                double delta = Math.min(3.0d, spendShare * 4.0d);
                ActionChange change = new ActionChange.VariantRequest(creativeId, winningAttributes(ctx, creativeId));
                String rationale = String.format(
                        "Request variants from the winner (score %.1f, MATURE) to scale what works.", score);
                out.add(new Candidate(AdzumpActionAuditActionType.REQUEST_VARIANT, row.getAdGrainId(), change,
                        rationale, delta, 0.55d, Risk.LOW, false, false, row.getSignalMaturity(),
                        clicks, 0L, 0L, 0L, 0L));
            }
        }

        return out;
    }

    /**
     * Best-effort resolution of the plan creative behind an AD-grain row. The snapshot carries platform
     * ad ids and the plan carries internal ids with no per-ad bridge, so in P3 this resolves only when
     * the plan has a single creative. <b>TODO:</b> a per-ad id map (or a creative grain in J10) lets
     * this bind each ad-grain to its creative.
     */
    private static String resolveCreativeId(AnalyzerContext ctx) {
        List<Creative> creatives = creatives(ctx);
        return creatives != null && creatives.size() == 1 ? creatives.getFirst().getId() : null;
    }

    private static Map<String, String> winningAttributes(AnalyzerContext ctx, String creativeId) {
        List<Creative> creatives = creatives(ctx);
        if (creatives == null || creativeId == null)
            return Map.of();
        for (Creative c : creatives)
            if (creativeId.equals(c.getId()) && c.getAttributes() != null)
                return c.getAttributes();
        return Map.of();
    }

    private static List<Creative> creatives(AnalyzerContext ctx) {
        CampaignPlanBody body = ctx.plan() == null ? null : ctx.plan().getBody();
        return body == null ? null : body.getCreatives();
    }
}
