package com.modlix.saas.adzump.service.competition;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.modlix.saas.adzump.model.competition.CompetitorAd;
import com.modlix.saas.adzump.model.competition.MarketContext;
import com.modlix.saas.adzump.model.competition.ProxyScore;
import com.modlix.saas.adzump.model.competition.RankedCompetitorAd;
import com.modlix.saas.adzump.vertical.ProxyWeights;

/**
 * The J19 "best-working proxy" (J19 §5.2) — the distinct logic of competition research.
 *
 * <p>The Ad Library has <b>no</b> performance metrics for commercial ads (no spend, impressions, CTR or
 * conversions). So we cannot read what performs; we <b>infer</b> it from signals that correlate with a
 * rational advertiser's own belief — advertisers kill losers and pour iteration into winners:
 * <ul>
 *   <li><b>Longevity</b> — continuous days running (weighted most): a long-lived ad is unlikely to be
 *       pure waste.</li>
 *   <li><b>Iteration density</b> — how many near-variants the same advertiser runs on the same theme:
 *       a theme they keep re-cutting is one they invest in.</li>
 *   <li><b>Recency</b> — currently active beats long-stopped.</li>
 *   <li><b>Reach</b> — used only when present (political/issue ads), as a bonus, never required.</li>
 *   <li><b>Breadth</b> — the same theme across multiple competitors (market-level corroboration).</li>
 * </ul>
 * The weights are vertical-tunable (J5, {@link ProxyWeights}); the scorer never hardcodes them. The
 * output is a shortlist <b>ranked by proxy, explicitly labeled proxy — not performance</b>.
 *
 * <p><b>Honest caveats (J19 §5.4), stated not buried:</b> longevity can mislead (a set-and-forget ad
 * nobody optimized looks "good"); with no spend/impressions, "best-working" is belief-revealed, not
 * outcome-proven; Ad Library coverage and fields vary by region/category. So this output is a
 * <i>hypothesis source</i> for generation, gated downstream by the real loop (leadzump outcomes,
 * J10/J20) — it never claims measurement.
 *
 * <p>Pure and stateless: {@link #score} takes everything it needs in the {@link MarketContext} (weights,
 * the {@code asOf} anchor, and the peer corpus for the two market-level signals), so it is fully
 * unit-testable with no I/O.
 */
@Component
public class BestWorkingProxy {

    /** Days of continuous running at which longevity saturates to 1.0 (~a quarter). */
    static final double LONGEVITY_SATURATION_DAYS = 90.0;

    /** Days after an ad stops over which recency decays from 1.0 to 0.0. */
    static final double RECENCY_WINDOW_DAYS = 90.0;

    /** Near-variant count (same advertiser + theme) at which iteration density saturates. */
    static final int ITERATION_SATURATION_COUNT = 5;

    /** Distinct-competitor count on one theme at which breadth saturates. */
    static final int BREADTH_SATURATION_COUNT = 3;

    /** Max characters of the creative-body fingerprint used as a theme fallback pre-vision. */
    private static final int FINGERPRINT_LEN = 48;

    /**
     * Scores one ad against the market context. Every component is normalized to {@code 0..1} and the
     * total is their weight-blended sum. Longevity uses {@code start → (stop or asOf)}; recency treats a
     * still-running ad as fully recent; iteration/breadth read the context corpus; reach contributes only
     * when the ad carries it. Pure — no I/O, deterministic given {@code ctx}.
     */
    public ProxyScore score(CompetitorAd ad, MarketContext ctx) {

        LocalDate asOf = ctx.asOf() == null ? LocalDate.now() : ctx.asOf();
        ProxyWeights w = ctx.weights() == null ? ProxyWeights.defaults() : ctx.weights();

        double longevity = longevity(ad, asOf);
        double recency = recency(ad, asOf);
        double iteration = iteration(ad, ctx.corpus());
        double breadth = breadth(ad, ctx.corpus());
        double reachBonus = reachBonus(ad, ctx.corpus());

        double total = w.longevity() * longevity
                + w.iteration() * iteration
                + w.recency() * recency
                + w.reach() * reachBonus
                + w.breadth() * breadth;

        return new ProxyScore(longevity, iteration, recency, reachBonus, breadth, total);
    }

    /**
     * De-dups the ads, scores each against the corpus, and returns the shortlist ranked by descending
     * proxy total (ties broken by longer run, then earlier start). Rank is 1-based. This is the
     * <b>proxy-ranked</b> output J19 persists and A4/the Competition tab consume.
     */
    public List<RankedCompetitorAd> rank(List<CompetitorAd> ads, String vertical, LocalDate asOf, ProxyWeights weights) {

        List<CompetitorAd> corpus = dedup(ads);
        ProxyWeights w = weights == null ? ProxyWeights.defaults() : weights;
        LocalDate anchor = asOf == null ? LocalDate.now() : asOf;
        MarketContext ctx = new MarketContext(vertical, anchor, w, corpus);

        List<Scored> scored = new ArrayList<>(corpus.size());
        for (CompetitorAd ad : corpus)
            scored.add(new Scored(ad, score(ad, ctx)));

        scored.sort(Comparator
                .comparingDouble((Scored s) -> s.score().total()).reversed()
                .thenComparing(s -> daysRunning(s.ad(), anchor), Comparator.reverseOrder())
                .thenComparing(s -> startOrMax(s.ad()))
                .thenComparing(s -> nullToEmpty(s.ad().getId())));

        List<RankedCompetitorAd> ranked = new ArrayList<>(scored.size());
        for (int i = 0; i < scored.size(); i++)
            ranked.add(new RankedCompetitorAd(i + 1, scored.get(i).ad(), scored.get(i).score()));

        return ranked;
    }

    // --- components -----------------------------------------------------------------------------

    private static double longevity(CompetitorAd ad, LocalDate asOf) {
        long days = daysRunning(ad, asOf);
        if (days <= 0)
            return 0.0;
        return Math.min(days / LONGEVITY_SATURATION_DAYS, 1.0);
    }

    private static double recency(CompetitorAd ad, LocalDate asOf) {
        LocalDate stop = ad.getDeliveryStop();
        if (stop == null || !stop.isBefore(asOf))
            return 1.0; // still running (or stopping today/future) => fully recent
        long sinceStop = ChronoUnit.DAYS.between(stop, asOf);
        if (sinceStop >= RECENCY_WINDOW_DAYS)
            return 0.0;
        return Math.max(0.0, 1.0 - (sinceStop / RECENCY_WINDOW_DAYS));
    }

    /** Density = size of the same-advertiser + same-theme group this ad belongs to, saturating. */
    private double iteration(CompetitorAd ad, List<CompetitorAd> corpus) {
        String theme = themeToken(ad);
        String page = ad.getPageId();
        int count = 0;
        for (CompetitorAd other : corpus) {
            if (sameKey(page, other.getPageId()) && sameKey(theme, themeToken(other)))
                count++;
        }
        return saturateCount(count, ITERATION_SATURATION_COUNT);
    }

    /** Corroboration = distinct competitors running this ad's theme; 0 when only one advertiser runs it. */
    private double breadth(CompetitorAd ad, List<CompetitorAd> corpus) {
        String theme = themeToken(ad);
        if (theme == null)
            return 0.0; // no theme => nothing to corroborate across competitors
        java.util.Set<String> advertisers = new java.util.HashSet<>();
        for (CompetitorAd other : corpus) {
            if (sameKey(theme, themeToken(other)) && other.getPageId() != null)
                advertisers.add(other.getPageId());
        }
        return saturateCount(advertisers.size(), BREADTH_SATURATION_COUNT);
    }

    /** Reach bonus only when this ad carries reach; normalized against the corpus max. Else 0 (no penalty). */
    private static double reachBonus(CompetitorAd ad, List<CompetitorAd> corpus) {
        Long reach = ad.getReach();
        if (reach == null || reach <= 0)
            return 0.0;
        long max = 0;
        for (CompetitorAd other : corpus) {
            Long r = other.getReach();
            if (r != null && r > max)
                max = r;
        }
        if (max <= 0)
            return 0.0;
        return Math.min((double) reach / max, 1.0);
    }

    // --- de-dup + theme -------------------------------------------------------------------------

    /**
     * Removes exact duplicate ads (same archive id, or — when id is absent — same advertiser + creative +
     * start), keeping first occurrence. This collapses paging overlap; it does <b>not</b> collapse genuine
     * near-variants (distinct ids), which are the signal iteration density measures.
     */
    List<CompetitorAd> dedup(List<CompetitorAd> ads) {
        Map<String, CompetitorAd> seen = new LinkedHashMap<>();
        if (ads == null)
            return new ArrayList<>();
        for (CompetitorAd ad : ads) {
            if (ad == null)
                continue;
            seen.putIfAbsent(identity(ad), ad);
        }
        return new ArrayList<>(seen.values());
    }

    private static String identity(CompetitorAd ad) {
        if (ad.getId() != null && !ad.getId().isBlank())
            return "id:" + ad.getId();
        return "c:" + nullToEmpty(ad.getPageId())
                + "|" + nullToEmpty(ad.getCreativeBody())
                + "|" + (ad.getDeliveryStart() == null ? "" : ad.getDeliveryStart());
    }

    /** Theme grouping key: the vision theme when set, else a coarse creative-body fingerprint, else null. */
    static String themeToken(CompetitorAd ad) {
        if (ad.getThemeKey() != null && !ad.getThemeKey().isBlank())
            return ad.getThemeKey().trim().toLowerCase(Locale.ROOT);
        String body = ad.getCreativeBody();
        if (body == null || body.isBlank())
            return null;
        String norm = body.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
        if (norm.isEmpty())
            return null;
        return norm.length() > FINGERPRINT_LEN ? norm.substring(0, FINGERPRINT_LEN) : norm;
    }

    // --- helpers --------------------------------------------------------------------------------

    private static long daysRunning(CompetitorAd ad, LocalDate asOf) {
        LocalDate start = ad.getDeliveryStart();
        if (start == null)
            return 0;
        LocalDate end = ad.getDeliveryStop() == null || ad.getDeliveryStop().isAfter(asOf)
                ? asOf
                : ad.getDeliveryStop();
        return ChronoUnit.DAYS.between(start, end);
    }

    /** Maps a group size to 0..1: 1 member => 0, saturationAt+ members => 1 (linear between). */
    private static double saturateCount(int count, int saturationAt) {
        if (count <= 1 || saturationAt <= 1)
            return count <= 1 ? 0.0 : 1.0;
        return Math.min((double) (count - 1) / (saturationAt - 1), 1.0);
    }

    private static boolean sameKey(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static LocalDate startOrMax(CompetitorAd ad) {
        return ad.getDeliveryStart() == null ? LocalDate.MAX : ad.getDeliveryStart();
    }

    private record Scored(CompetitorAd ad, ProxyScore score) {
    }
}
