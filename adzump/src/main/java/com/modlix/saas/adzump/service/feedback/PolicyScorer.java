package com.modlix.saas.adzump.service.feedback;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.modlix.saas.adzump.dto.PerformancePolicy;
import com.modlix.saas.adzump.model.Money;
import com.modlix.saas.adzump.model.leadzump.Grain;
import com.modlix.saas.adzump.model.snapshot.CrmMetrics;
import com.modlix.saas.adzump.model.snapshot.PlatformMetrics;
import com.modlix.saas.adzump.model.snapshot.SignalMaturity;
import com.modlix.saas.adzump.model.snapshot.SnapshotRow;

/**
 * Turns a joined {@link SnapshotRow} into the single 0..100 blended score J12 optimizes and A5
 * explains (J10 §5.4). <b>Deterministic Java, config-driven, never an LLM</b>: the knobs come from
 * the effective {@link PerformancePolicy} (CREATIVE §1 — a platform layer, a conversion layer, and
 * the per-milestone leadzump layers, each weighted and volume-gated).
 *
 * <p><b>Layers (each contributes only when its volume gate is met).</b>
 * <ul>
 * <li><b>Platform</b> (fast, higher-is-better CTR): {@code clamp(ctr / targetCtr) * 100}, gated by
 * {@code minImpressions}.</li>
 * <li><b>Conversion</b> (mid, lower-is-better CPL): {@code clamp(targetCpl / actualCpl) * 100} where
 * {@code actualCpl = spend / entry-milestone leads}, gated by {@code minClicks}.</li>
 * <li><b>leadzump per-stage</b> (slow, decisive, lower-is-better unit cost):
 * {@code clamp(targetCost / unitCost) * 100} for each configured stage, gated by that stage's
 * {@code minCount}.</li>
 * </ul>
 * The blended score is the weight-normalised mean over the layers that passed their gate, then
 * scaled down by {@code junkPenalty * junkRate}. Because a leadzump stage drops out until its
 * {@code minCount} is met, the fast layers guide provisionally and the slow stages take over as they
 * mature — the fast&rarr;slow handoff of CREATIVE §1.4.
 *
 * <p>Config schema read from {@link PerformancePolicy#getBody()} (all fields optional; a missing
 * layer simply does not contribute):
 * <pre>{@code
 * {
 *   "platform":   { "weight": 0.15, "targetCtr": 0.02, "minImpressions": 1000 },
 *   "conversion": { "weight": 0.20, "targetCpl": 350,  "minClicks": 50 },
 *   "leadzumpStages": [
 *     { "stage": "lead",       "targetCost": 350,  "weight": 0.10, "minCount": 20 },
 *     { "stage": "qualified",  "targetCost": 800,  "weight": 0.25, "minCount": 10 },
 *     { "stage": "site_visit", "targetCost": 2500, "weight": 0.20, "minCount": 5  },
 *     { "stage": "booking",    "targetCost": 7000, "weight": 0.25, "minCount": 2  }
 *   ],
 *   "junkPenalty": 0.5
 * }
 * }</pre>
 * Stage keys are matched against the milestone-folded CRM keys case-insensitively.
 */
@Service
public class PolicyScorer {

    /**
     * The 0..100 blended score for one joined row under the effective policy. Returns {@code 0.0}
     * when no layer's volume gate is met (nothing trustworthy to score yet).
     */
    public double score(SnapshotRow row, PerformancePolicy policy) {

        if (row == null)
            return 0.0d;

        ScoringConfig cfg = ScoringConfig.parse(policy == null ? null : policy.getBody());

        List<double[]> layers = new ArrayList<>(); // each entry = { subScore, weight }

        PlatformMetrics platform = row.getPlatform();

        // --- platform layer (higher CTR is better) --------------------------------------------
        if (platform != null && cfg.platformWeight > 0 && cfg.platformTargetCtr > 0
                && platform.getImpressions() >= cfg.platformMinImpressions) {
            double sub = clamp01(platform.getCtr() / cfg.platformTargetCtr) * 100.0d;
            layers.add(new double[] { sub, cfg.platformWeight });
        }

        // --- conversion layer (lower CPL is better): CPL = spend / entry-milestone leads -------
        long leads = cfg.leadzumpStages.isEmpty() ? 0L : crmCount(row.getCrm(), cfg.leadzumpStages.getFirst().stage);
        Double spendAmount = amount(platform == null ? null : platform.getSpend());
        if (platform != null && cfg.conversionWeight > 0 && cfg.conversionTargetCpl != null
                && platform.getClicks() >= cfg.conversionMinClicks && leads > 0 && spendAmount != null) {
            double actualCpl = spendAmount / leads;
            double sub = actualCpl > 0 ? clamp01(cfg.conversionTargetCpl / actualCpl) * 100.0d : 100.0d;
            layers.add(new double[] { sub, cfg.conversionWeight });
        }

        // --- leadzump per-stage layers (lower unit cost is better) -----------------------------
        for (StageConfig stage : cfg.leadzumpStages) {
            long count = crmCount(row.getCrm(), stage.stage);
            if (stage.weight <= 0 || stage.targetCost == null || count <= 0 || count < stage.minCount)
                continue;
            Double unitCost = unitCost(row, stage.stage, count);
            if (unitCost == null || unitCost <= 0)
                continue;
            double sub = clamp01(stage.targetCost / unitCost) * 100.0d;
            layers.add(new double[] { sub, stage.weight });
        }

        if (layers.isEmpty())
            return 0.0d;

        double weighted = 0.0d;
        double weightSum = 0.0d;
        for (double[] layer : layers) {
            weighted += layer[0] * layer[1];
            weightSum += layer[1];
        }
        double blended = weightSum > 0 ? weighted / weightSum : 0.0d;

        double junk = row.getCrm() == null ? 0.0d : row.getCrm().getJunkRate();
        blended *= (1.0d - cfg.junkPenalty * junk);

        return clamp(blended, 0.0d, 100.0d);
    }

    /**
     * Classifies the fast/slow maturity of a row (J10 §5.3). {@link SignalMaturity#FAST_ONLY} when no
     * CRM outcome has joined; {@link SignalMaturity#MATURE} once any <i>downstream</i> stage (any
     * configured stage after the entry stage) has reached its {@code minCount} gate;
     * {@link SignalMaturity#PARTIAL} otherwise (leads present but the slow signal is still too thin to
     * trust a kill).
     */
    public SignalMaturity classifyMaturity(SnapshotRow row, PerformancePolicy policy) {

        CrmMetrics crm = row == null ? null : row.getCrm();
        if (crm == null || totalCount(crm) == 0)
            return SignalMaturity.FAST_ONLY;

        ScoringConfig cfg = ScoringConfig.parse(policy == null ? null : policy.getBody());

        // No downstream stage configured -> the slow signal cannot be established -> PARTIAL.
        for (int i = 1; i < cfg.leadzumpStages.size(); i++) {
            StageConfig stage = cfg.leadzumpStages.get(i);
            long count = crmCount(crm, stage.stage);
            if (count > 0 && count >= stage.minCount)
                return SignalMaturity.MATURE;
        }

        return SignalMaturity.PARTIAL;
    }

    /**
     * Rolls the per-row scores up into the campaign-level number (J10 §5.4). To avoid double-counting
     * across grains (a campaign's spend is the sum of its ad-sets'), the rollup is taken over the
     * <b>coarsest grain present</b> only, spend-weighted (arithmetic mean when total spend is zero).
     */
    public double rollup(List<SnapshotRow> rows) {

        if (rows == null || rows.isEmpty())
            return 0.0d;

        Grain coarsest = null;
        for (SnapshotRow row : rows) {
            Grain g = row.getGrain();
            if (g == null)
                continue;
            if (coarsest == null || g.ordinal() < coarsest.ordinal())
                coarsest = g;
        }

        double weighted = 0.0d;
        double spendSum = 0.0d;
        double scoreSum = 0.0d;
        int n = 0;
        for (SnapshotRow row : rows) {
            if (coarsest != null && row.getGrain() != coarsest)
                continue;
            double score = row.getBlendedScore();
            Double spend = amount(row.getPlatform() == null ? null : row.getPlatform().getSpend());
            double s = spend == null ? 0.0d : spend;
            weighted += score * s;
            spendSum += s;
            scoreSum += score;
            n++;
        }

        if (n == 0)
            return 0.0d;
        return spendSum > 0 ? weighted / spendSum : scoreSum / n;
    }

    // ------------------------------------------------------------------------------------------
    // CRM lookups (case-insensitive against the milestone-folded keys)
    // ------------------------------------------------------------------------------------------

    private static long crmCount(CrmMetrics crm, String milestone) {
        if (crm == null || crm.getCountByMilestone() == null || milestone == null)
            return 0L;
        for (Map.Entry<String, Long> e : crm.getCountByMilestone().entrySet()) {
            if (milestone.equalsIgnoreCase(e.getKey()))
                return e.getValue() == null ? 0L : e.getValue();
        }
        return 0L;
    }

    /** Unit cost for a milestone: the folded {@code costByMilestone}, or {@code spend/count} as a fallback. */
    private static Double unitCost(SnapshotRow row, String milestone, long count) {
        CrmMetrics crm = row.getCrm();
        if (crm != null && crm.getCostByMilestone() != null && milestone != null) {
            for (Map.Entry<String, Money> e : crm.getCostByMilestone().entrySet()) {
                if (milestone.equalsIgnoreCase(e.getKey())) {
                    Double a = amount(e.getValue());
                    if (a != null)
                        return a;
                }
            }
        }
        Double spend = amount(row.getPlatform() == null ? null : row.getPlatform().getSpend());
        return (spend != null && count > 0) ? spend / count : null;
    }

    private static long totalCount(CrmMetrics crm) {
        if (crm == null || crm.getCountByMilestone() == null)
            return 0L;
        long total = 0L;
        for (Long v : crm.getCountByMilestone().values())
            total += v == null ? 0L : v;
        return total;
    }

    private static Double amount(Money money) {
        return money == null || money.getAmount() == null ? null : money.getAmount().doubleValue();
    }

    private static double clamp01(double v) {
        return clamp(v, 0.0d, 1.0d);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ------------------------------------------------------------------------------------------
    // Parsed policy config
    // ------------------------------------------------------------------------------------------

    private record StageConfig(String stage, Double targetCost, double weight, long minCount) {
    }

    private static final class ScoringConfig {

        private double platformWeight;
        private double platformTargetCtr;
        private long platformMinImpressions;

        private double conversionWeight;
        private Double conversionTargetCpl;
        private long conversionMinClicks;

        private final List<StageConfig> leadzumpStages = new ArrayList<>();

        private double junkPenalty;

        private static ScoringConfig parse(JsonNode body) {

            ScoringConfig cfg = new ScoringConfig();
            if (body == null || !body.isObject())
                return cfg;

            JsonNode platform = body.get("platform");
            if (platform != null && platform.isObject()) {
                cfg.platformWeight = platform.path("weight").asDouble(0.0d);
                cfg.platformTargetCtr = platform.path("targetCtr").asDouble(0.0d);
                cfg.platformMinImpressions = platform.path("minImpressions").asLong(0L);
            }

            JsonNode conversion = body.get("conversion");
            if (conversion != null && conversion.isObject()) {
                cfg.conversionWeight = conversion.path("weight").asDouble(0.0d);
                JsonNode targetCpl = conversion.get("targetCpl");
                cfg.conversionTargetCpl = targetCpl == null || targetCpl.isNull() ? null : targetCpl.asDouble();
                cfg.conversionMinClicks = conversion.path("minClicks").asLong(0L);
            }

            JsonNode stages = body.get("leadzumpStages");
            if (stages != null && stages.isArray()) {
                for (JsonNode s : stages) {
                    String stageKey = s.path("stage").asText(null);
                    if (stageKey == null || stageKey.isBlank())
                        continue;
                    JsonNode targetCost = s.get("targetCost");
                    Double target = targetCost == null || targetCost.isNull() ? null : targetCost.asDouble();
                    cfg.leadzumpStages.add(new StageConfig(
                            stageKey,
                            target,
                            s.path("weight").asDouble(0.0d),
                            s.path("minCount").asLong(0L)));
                }
            }

            cfg.junkPenalty = body.path("junkPenalty").asDouble(0.0d);

            return cfg;
        }
    }
}
