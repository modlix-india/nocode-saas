package com.modlix.saas.adzump.service.creative;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

import org.springframework.stereotype.Service;

import com.modlix.saas.adzump.dao.CreativeAttributeDao;
import com.modlix.saas.adzump.dao.PerformanceSnapshotDao;
import com.modlix.saas.adzump.dto.CreativeAttributeRow;
import com.modlix.saas.adzump.dto.PerformanceSnapshotEntity;
import com.modlix.saas.adzump.model.snapshot.PerformanceSnapshotBody;
import com.modlix.saas.adzump.model.snapshot.SnapshotRow;
import com.modlix.saas.adzump.model.snapshot.SnapshotWindow;
import com.modlix.saas.adzump.vertical.AttributeTaxonomy;
import com.modlix.saas.adzump.vertical.VerticalRegistry;

/**
 * J20 §5.2 — attribute attribution, the core learning. Decomposes realized creative outcomes onto the
 * creatives' J5 attribute values so the loop optimises on <b>quality of angle</b> (which
 * angle/scene/offer/CTA works) rather than on opaque ad ids.
 *
 * <p><b>Regularized lift, not a naive mean.</b> A value's standing is the volume-weighted observed mean
 * <i>shrunk toward the prior/baseline by a pseudo-count</i> (empirical-Bayes shrinkage), and its
 * confidence is capped by <b>both</b> outcome volume and the number of distinct creatives that carried
 * it — so an attribute seen on two creatives (however loud) is not declared a winner (§8). Immature
 * creatives (slow signal not yet matured) are down-weighted, not judged. The leadzump junk signal folds
 * in as a per-value junk correlation that gates the exploit decision (§5.2).
 *
 * <p><b>Cold start.</b> With no realized volume the map leans on {@link MarketPriors} (J19) and the
 * vertical baseline, flagging {@link AttributeAttribution#coldStart()} so the predictor surfaces low
 * confidence and A4 soft-ranks rather than hard-blocks (§5.3).
 *
 * <p>Deterministic Java, config-driven — never an LLM.
 */
@Service
public class AttributeAttributor {

    /** Neutral 0..100 baseline when neither the account nor a prior offers one. */
    static final double NEUTRAL_BASELINE = 50.0d;

    /** Shrinkage strength: outcome volume needed to half-trust the observed mean over the prior. */
    static final double VOLUME_PSEUDOCOUNT = 20.0d;

    /** Breadth shrinkage: distinct-creative pseudo-count that caps confidence on narrow evidence. */
    static final double CREATIVE_PSEUDOCOUNT = 2.0d;

    /** An immature creative's evidence counts at this fraction (not judged on thin/immature data). */
    static final double IMMATURE_WEIGHT = 0.5d;

    // Exploit (winner) gate.
    static final int MIN_WINNER_CREATIVES = 3;
    static final double WINNER_CONFIDENCE = 0.5d;
    static final double WINNER_MIN_LIFT = 3.0d;
    static final double WINNER_MAX_JUNK = 0.10d;

    private final CreativeScorer creativeScorer;
    private final CreativeAttributeDao creativeAttributeDao;
    private final PerformanceSnapshotDao snapshotDao;
    private final VerticalRegistry verticalRegistry;
    private final MarketPriors marketPriors;

    public AttributeAttributor(CreativeScorer creativeScorer, CreativeAttributeDao creativeAttributeDao,
            PerformanceSnapshotDao snapshotDao, VerticalRegistry verticalRegistry, MarketPriors marketPriors) {

        this.creativeScorer = creativeScorer;
        this.creativeAttributeDao = creativeAttributeDao;
        this.snapshotDao = snapshotDao;
        this.verticalRegistry = verticalRegistry;
        this.marketPriors = marketPriors;
    }

    /**
     * Builds the account's attribute performance map for {@code clientCode} in {@code vertical} over
     * {@code window}. Gathers the creatives' attribute tags ({@code adzump_creative_attribute}) and their
     * realized outcomes (the latest snapshot per campaign, joined at the creative grain via
     * {@link CreativeScorer}), then delegates to {@link #aggregate}. Both DAO reads are scoped to
     * {@code clientCode}, so the map is tenant-private (§5.5).
     */
    public AttributeAttribution attribute(String clientCode, String vertical, SnapshotWindow window) {

        Map<String, Map<String, String>> attributesByCreative = this.loadCreativeAttributes(clientCode);
        List<SnapshotRow> rows = this.loadOutcomeRows(clientCode, window);

        List<CreativeOutcome> outcomes = new ArrayList<>(attributesByCreative.size());
        for (Map.Entry<String, Map<String, String>> e : attributesByCreative.entrySet()) {
            CreativeScore score = this.creativeScorer.score(e.getKey(), rows);
            outcomes.add(CreativeOutcome.of(score, e.getValue()));
        }

        return this.aggregate(clientCode, vertical, window, outcomes);
    }

    /**
     * The pure attribution math over already-joined per-creative outcomes (no DB / no security), so the
     * regularization, junk-correlation, and cold-start behaviour is unit-testable from fixtures.
     */
    public AttributeAttribution aggregate(String clientCode, String vertical, SnapshotWindow window,
            List<CreativeOutcome> outcomes) {

        // --- account baseline (volume-weighted, immature-discounted) over all realized outcomes -----
        double baseWeight = 0.0d;
        double baseScore = 0.0d;
        double baseJunk = 0.0d;
        for (CreativeOutcome o : outcomes) {
            double w = evidence(o);
            if (w <= 0)
                continue;
            baseWeight += w;
            baseScore += w * o.score();
            baseJunk += w * o.junkRate();
        }

        boolean coldStart = baseWeight <= 0;
        double baseline = coldStart
                ? or(this.marketPriors.priorBaseline(vertical), NEUTRAL_BASELINE)
                : baseScore / baseWeight;
        double baselineJunk = coldStart ? 0.0d : baseJunk / baseWeight;

        // --- per-(axis,value) accumulation ---------------------------------------------------------
        Map<String, Accum> byValue = new LinkedHashMap<>();
        if (!coldStart) {
            for (CreativeOutcome o : outcomes) {
                double w = evidence(o);
                if (w <= 0 || o.attributes().isEmpty())
                    continue;
                for (Map.Entry<String, String> attr : o.attributes().entrySet()) {
                    Accum a = byValue.computeIfAbsent(key(attr.getKey(), attr.getValue()),
                            k -> new Accum(attr.getKey(), attr.getValue()));
                    a.weight += w;
                    a.score += w * o.score();
                    a.junk += w * o.junkRate();
                    a.volume += o.volume();
                    a.creativeCount++;
                }
            }
        }

        List<AttributeStat> stats = new ArrayList<>(byValue.size());
        for (Accum a : byValue.values())
            stats.add(this.toStat(a, vertical, baseline, baselineJunk));

        List<AttributeStat> unexplored = this.frontier(vertical, baseline, byValue.keySet());

        return new AttributeAttribution(clientCode, vertical, window, baseline, stats, unexplored, coldStart);
    }

    // ==============================================================================================

    private AttributeStat toStat(Accum a, String vertical, double baseline, double baselineJunk) {

        double obsMean = a.score / a.weight;
        double priorMean = or(this.marketPriors.priorScore(vertical, a.axis, a.value), baseline);

        // Empirical-Bayes shrinkage of the observed mean toward the prior/baseline by the pseudo-count:
        // thin evidence stays near the prior (no wild swing), strong evidence trusts the observation.
        double regScore = (a.weight * obsMean + VOLUME_PSEUDOCOUNT * priorMean) / (a.weight + VOLUME_PSEUDOCOUNT);
        double lift = regScore - baseline;

        double junkMean = a.junk / a.weight;
        double junkCorrelation = junkMean - baselineJunk;

        // Confidence is capped by BOTH volume and breadth — one loud creative cannot "prove" a value.
        double confVolume = a.weight / (a.weight + VOLUME_PSEUDOCOUNT);
        double confBreadth = a.creativeCount / (a.creativeCount + CREATIVE_PSEUDOCOUNT);
        double confidence = Math.min(confVolume, confBreadth);

        boolean winner = confidence >= WINNER_CONFIDENCE
                && a.creativeCount >= MIN_WINNER_CREATIVES
                && lift >= WINNER_MIN_LIFT
                && junkCorrelation <= WINNER_MAX_JUNK;

        boolean underExplored = !winner
                && (a.creativeCount < MIN_WINNER_CREATIVES || confidence < WINNER_CONFIDENCE);

        return new AttributeStat(a.axis, a.value, round(regScore), round(baseline), round(lift),
                a.volume, a.creativeCount, round(confidence), round(junkCorrelation), winner, underExplored);
    }

    /** The pure-explore frontier: taxonomy values with no realized evidence yet, prior/baseline-scored. */
    private List<AttributeStat> frontier(String vertical, double baseline, java.util.Set<String> observedKeys) {

        AttributeTaxonomy taxonomy = this.verticalRegistry.getOrDefault(vertical).attributeTaxonomy();
        List<AttributeStat> out = new ArrayList<>();

        for (String axis : taxonomy.axisNames()) {
            for (String value : taxonomy.valuesFor(axis)) {
                if (observedKeys.contains(key(axis, value)))
                    continue;
                double prior = or(this.marketPriors.priorScore(vertical, axis, value), baseline);
                out.add(new AttributeStat(axis, value, round(prior), round(baseline), round(prior - baseline),
                        0L, 0, 0.0d, 0.0d, false, true));
            }
        }

        return out;
    }

    // ---- outcome gathering (tenant-scoped DAO reads) ---------------------------------------------

    private Map<String, Map<String, String>> loadCreativeAttributes(String clientCode) {

        Map<String, Map<String, String>> byCreative = new LinkedHashMap<>();
        for (CreativeAttributeRow row : this.creativeAttributeDao.findByClient(clientCode)) {
            if (row.getCreativeId() == null || row.getAxis() == null || row.getValue() == null)
                continue;
            byCreative.computeIfAbsent(row.getCreativeId(), k -> new LinkedHashMap<>())
                    .putIfAbsent(row.getAxis(), row.getValue());
        }
        return byCreative;
    }

    /** The AD-grain rows of the latest snapshot per campaign for the client, within the window. */
    private List<SnapshotRow> loadOutcomeRows(String clientCode, SnapshotWindow window) {

        List<SnapshotRow> rows = new ArrayList<>();
        java.util.Set<Object> seenCampaigns = new java.util.HashSet<>();

        // findByClient returns newest-first, so the first row seen per campaign is its latest snapshot.
        for (PerformanceSnapshotEntity e : this.snapshotDao.findByClient(clientCode)) {
            if (!withinWindow(e, window) || !seenCampaigns.add(e.getCampaignPlanId()))
                continue;
            PerformanceSnapshotBody body = e.getBody();
            if (body != null && body.getGrainRows() != null)
                rows.addAll(body.getGrainRows());
        }

        return rows;
    }

    private static boolean withinWindow(PerformanceSnapshotEntity e, SnapshotWindow window) {
        if (window == null)
            return true;
        if (window.getFrom() != null && e.getWindowTo() != null && e.getWindowTo().isBefore(window.getFrom()))
            return false;
        return !(window.getTo() != null && e.getWindowFrom() != null && e.getWindowFrom().isAfter(window.getTo()));
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private static double evidence(CreativeOutcome o) {
        if (o.volume() <= 0)
            return 0.0d;
        return o.judgeable() ? o.volume() : o.volume() * IMMATURE_WEIGHT;
    }

    private static String key(String axis, String value) {
        return axis + " " + value;
    }

    private static double or(OptionalDouble opt, double fallback) {
        return opt.isPresent() ? opt.getAsDouble() : fallback;
    }

    private static double round(double v) {
        return Math.round(v * 1_000_000.0d) / 1_000_000.0d;
    }

    /** Mutable per-value accumulator. */
    private static final class Accum {
        private final String axis;
        private final String value;
        private double weight;
        private double score;
        private double junk;
        private long volume;
        private int creativeCount;

        private Accum(String axis, String value) {
            this.axis = axis;
            this.value = value;
        }
    }
}
