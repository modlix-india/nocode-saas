package com.modlix.saas.adzump.service.competition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.model.competition.CompetitorAd;
import com.modlix.saas.adzump.model.competition.MarketContext;
import com.modlix.saas.adzump.model.competition.ProxyScore;
import com.modlix.saas.adzump.model.competition.RankedCompetitorAd;
import com.modlix.saas.adzump.vertical.ProxyWeights;

/**
 * Pure (no Spring, no JOOQ) tests for the J19 best-working proxy: longevity + iteration dominate the
 * ranking as designed, exact duplicates are de-duped (without inflating iteration), reach is used only
 * when present, breadth needs multiple competitors, and the theme fingerprint falls back to the creative
 * copy pre-vision. All signals are belief-revealed proxies, never performance.
 */
class BestWorkingProxyTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 7, 1);
    private static final String RE = "real_estate";

    private final BestWorkingProxy proxy = new BestWorkingProxy();

    private static CompetitorAd ad(String id, String pageId, String theme, int startDaysAgo, Integer stopDaysAgo) {
        return new CompetitorAd()
                .setId(id)
                .setPageId(pageId)
                .setThemeKey(theme)
                .setPlatform(Platform.META)
                .setDeliveryStart(AS_OF.minusDays(startDaysAgo))
                .setDeliveryStop(stopDaysAgo == null ? null : AS_OF.minusDays(stopDaysAgo));
    }

    private List<RankedCompetitorAd> rank(List<CompetitorAd> ads) {
        return this.proxy.rank(ads, RE, AS_OF, ProxyWeights.defaults());
    }

    // ---- ranking ---------------------------------------------------------------------------------

    @Test
    void longevityAndIterationDominateTheRanking() {

        // A long-lived, heavily iterated theme from one advertiser.
        CompetitorAd winner = ad("w", "P1", "roi", 120, null);          // longevity 1.0, active
        List<CompetitorAd> ads = new ArrayList<>(List.of(winner,
                ad("v1", "P1", "roi", 10, null),                        // re-cuts of the same theme
                ad("v2", "P1", "roi", 10, null),
                ad("v3", "P1", "roi", 10, null),
                ad("v4", "P1", "roi", 10, null)));                      // group size 5 => iteration 1.0
        // A long-lived but NON-iterated one-off from another advertiser.
        CompetitorAd loner = ad("loner", "P2", "solo", 200, null);      // longevity 1.0, iteration 0
        ads.add(loner);

        List<RankedCompetitorAd> ranked = rank(ads);

        // The long + iterated ad ranks first.
        assertEquals("w", ranked.getFirst().ad().getId());

        ProxyScore winnerScore = scoreOf(ranked, "w");
        ProxyScore lonerScore = scoreOf(ranked, "loner");
        ProxyScore variantScore = scoreOf(ranked, "v1");

        // Both winner and loner max out longevity + recency; iteration is what separates them.
        assertEquals(1.0, winnerScore.longevity(), 1e-9);
        assertEquals(1.0, lonerScore.longevity(), 1e-9);
        assertEquals(1.0, winnerScore.iteration(), 1e-9);
        assertEquals(0.0, lonerScore.iteration(), 1e-9);
        assertTrue(winnerScore.total() > lonerScore.total(),
                "iterated long-runner must beat the non-iterated long-runner");

        // A single high-weight signal (longevity) still lifts the loner above a short-lived re-cut.
        assertTrue(lonerScore.total() > variantScore.total(),
                "longevity weight dominates a short-lived but iterated variant");
    }

    @Test
    void rankIsProxyRankedAndOneBasedContiguous() {

        List<RankedCompetitorAd> ranked = rank(List.of(
                ad("a", "P1", "roi", 100, null),
                ad("b", "P2", "loc", 5, 1)));

        assertEquals(2, ranked.size());
        assertEquals(1, ranked.getFirst().rank());
        assertEquals(2, ranked.get(1).rank());
        // Sorted by descending proxy total.
        assertTrue(ranked.getFirst().score().total() >= ranked.get(1).score().total());
    }

    // ---- de-dup ----------------------------------------------------------------------------------

    @Test
    void exactDuplicatesAreDedupedAndDoNotInflateIteration() {

        CompetitorAd original = ad("dup", "P1", "roi", 100, null);
        CompetitorAd sameId = ad("dup", "P1", "roi", 100, null);       // same archive id => duplicate
        CompetitorAd sameIdAgain = ad("dup", "P1", "roi", 100, null);

        List<RankedCompetitorAd> ranked = rank(List.of(original, sameId, sameIdAgain));

        assertEquals(1, ranked.size(), "three copies of one ad collapse to one");
        // Group of one => no iteration inflation from the duplicates.
        assertEquals(0.0, ranked.getFirst().score().iteration(), 1e-9);
    }

    @Test
    void nearVariantsWithDistinctIdsAreNotDeduped() {

        // Same advertiser + theme, DIFFERENT ids => genuine re-cuts, the signal iteration measures.
        List<CompetitorAd> deduped = this.proxy.dedup(List.of(
                ad("x1", "P1", "roi", 10, null),
                ad("x2", "P1", "roi", 12, null),
                ad("x3", "P1", "roi", 14, null)));

        assertEquals(3, deduped.size());
    }

    // ---- reach -----------------------------------------------------------------------------------

    @Test
    void reachIsUsedOnlyWhenPresent() {

        CompetitorAd withReach = ad("r", "P1", "roi", 50, null).setReach(1000L);
        CompetitorAd withoutReach = ad("n", "P2", "loc", 50, null);   // reach null

        List<RankedCompetitorAd> ranked = rank(List.of(withReach, withoutReach));

        ProxyScore r = scoreOf(ranked, "r");
        ProxyScore n = scoreOf(ranked, "n");

        assertTrue(r.reachBonus() > 0.0, "reach present => reach bonus applies");
        assertEquals(0.0, n.reachBonus(), 1e-9, "reach absent => no bonus, no penalty");
        assertTrue(r.total() > n.total(), "the reach-bearing ad wins on the reach bonus, all else equal");
    }

    @Test
    void whenNoAdCarriesReachReachBonusIsZeroForAll() {

        List<RankedCompetitorAd> ranked = rank(List.of(
                ad("a", "P1", "roi", 90, null),
                ad("b", "P2", "loc", 30, null)));

        for (RankedCompetitorAd r : ranked)
            assertEquals(0.0, r.score().reachBonus(), 1e-9);
    }

    // ---- breadth ---------------------------------------------------------------------------------

    @Test
    void breadthNeedsMultipleCompetitorsOnTheSameTheme() {

        // Same theme "roi" across three DISTINCT advertisers => breadth corroboration.
        MarketContext broad = ctx(List.of(
                ad("a", "P1", "roi", 30, null),
                ad("b", "P2", "roi", 30, null),
                ad("c", "P3", "roi", 30, null)));
        assertEquals(1.0, this.proxy.score(broad.corpus().getFirst(), broad).breadth(), 1e-9);

        // One advertiser on a theme => no cross-competitor corroboration.
        MarketContext solo = ctx(List.of(ad("a", "P1", "solo", 30, null)));
        assertEquals(0.0, this.proxy.score(solo.corpus().getFirst(), solo).breadth(), 1e-9);
    }

    // ---- weights (J5-tunable) --------------------------------------------------------------------

    @Test
    void weightsChangeTheBlendedTotal() {

        MarketContext base = ctx(List.of(ad("a", "P1", "roi", 120, null)));
        CompetitorAd only = base.corpus().getFirst();

        double allLongevity = this.proxy.score(only,
                new MarketContext(RE, AS_OF, new ProxyWeights(1, 0, 0, 0, 0), base.corpus())).total();
        double allIteration = this.proxy.score(only,
                new MarketContext(RE, AS_OF, new ProxyWeights(0, 1, 0, 0, 0), base.corpus())).total();

        // A lone, long-lived ad: full weight on longevity scores high, full weight on iteration scores 0.
        assertEquals(1.0, allLongevity, 1e-9);
        assertEquals(0.0, allIteration, 1e-9);
        assertNotEquals(allLongevity, allIteration);
    }

    // ---- theme fingerprint fallback --------------------------------------------------------------

    @Test
    void themeTokenFallsBackToCreativeFingerprintWhenNoVisionTheme() {

        CompetitorAd a = new CompetitorAd().setCreativeBody("Assured ROI! Book now.");
        CompetitorAd b = new CompetitorAd().setCreativeBody("assured  roi   book now");   // same, normalized
        CompetitorAd c = new CompetitorAd().setCreativeBody("Lakeside villas, ready to move");

        assertEquals(BestWorkingProxy.themeToken(a), BestWorkingProxy.themeToken(b));
        assertNotEquals(BestWorkingProxy.themeToken(a), BestWorkingProxy.themeToken(c));

        // An explicit vision theme wins over the fingerprint.
        CompetitorAd tagged = new CompetitorAd().setThemeKey("investment_roi").setCreativeBody("whatever");
        assertEquals("investment_roi", BestWorkingProxy.themeToken(tagged));
    }

    @Test
    void stoppedAdsAreLessRecentThanRunningOnes() {

        MarketContext c = ctx(List.of(
                ad("running", "P1", "t", 100, null),
                ad("stoppedLongAgo", "P2", "t2", 100, 100)));            // stopped 100 days ago

        assertEquals(1.0, this.proxy.score(byId(c, "running"), c).recency(), 1e-9);
        assertEquals(0.0, this.proxy.score(byId(c, "stoppedLongAgo"), c).recency(), 1e-9);
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private MarketContext ctx(List<CompetitorAd> ads) {
        return new MarketContext(RE, AS_OF, ProxyWeights.defaults(), this.proxy.dedup(ads));
    }

    private static CompetitorAd byId(MarketContext ctx, String id) {
        return ctx.corpus().stream().filter(a -> id.equals(a.getId())).findFirst().orElseThrow();
    }

    private static ProxyScore scoreOf(List<RankedCompetitorAd> ranked, String id) {
        return ranked.stream().filter(r -> id.equals(r.ad().getId())).findFirst().orElseThrow().score();
    }
}
