package com.modlix.saas.adzump.service.creative;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

import org.junit.jupiter.api.Test;

import com.modlix.saas.adzump.dao.CreativeAttributeDao;
import com.modlix.saas.adzump.dao.PerformanceSnapshotDao;
import com.modlix.saas.adzump.dto.CreativeAttributeRow;
import com.modlix.saas.adzump.vertical.VerticalRegistry;
import com.modlix.saas.adzump.vertical.realestate.RealEstatePlaybook;

/**
 * Offline unit tests for {@link AttributeAttributor} — the core learning. Exercises the four behaviours
 * the design (J20 §5.2/§5.3/§5.4/§8) demands: <b>regularization</b> (a thin attribute is shrunk and not
 * declared a winner), the <b>junk-correlation penalty</b> (a junk-correlated attribute loses winner
 * status even at an equal score), <b>cold-start</b> fallback to priors/defaults, the <b>explore/exploit
 * surface</b>, and that the account read path is <b>tenant-scoped</b> by client code.
 */
class AttributeAttributorTest {

    private static final String CLIENT = "CLI0";
    private static final String RE = "real_estate";

    // ---- fixtures --------------------------------------------------------------------------------

    /** A priors source with an optional cold-start baseline and no per-value priors (shrink to baseline). */
    private record Priors(OptionalDouble baseline) implements MarketPriors {
        @Override
        public OptionalDouble priorScore(String vertical, String axis, String value) {
            return OptionalDouble.empty();
        }

        @Override
        public OptionalDouble priorBaseline(String vertical) {
            return this.baseline;
        }
    }

    private static AttributeAttributor attributor(MarketPriors priors) {
        VerticalRegistry vr = mock(VerticalRegistry.class);
        when(vr.getOrDefault(any())).thenReturn(new RealEstatePlaybook());
        return new AttributeAttributor(mock(CreativeScorer.class), mock(CreativeAttributeDao.class),
                mock(PerformanceSnapshotDao.class), vr, priors);
    }

    private static CreativeOutcome outcome(String id, Map<String, String> attrs, double score, long volume,
            double junk) {
        return new CreativeOutcome(id, attrs, score, volume, junk, true); // judgeable
    }

    private static void add(List<CreativeOutcome> list, int n, String axis, String value, double score, long volume,
            double junk) {
        int base = list.size();
        for (int i = 0; i < n; i++)
            list.add(outcome("cr" + (base + i), Map.of(axis, value), score, volume, junk));
    }

    // ---- tests -----------------------------------------------------------------------------------

    @Test
    void regularization_thinVolumeAttributeIsShrunkAndNotAWinner() {

        List<CreativeOutcome> outcomes = new ArrayList<>();
        add(outcomes, 6, "angle", "location", 70.0, 50, 0.05);        // sets the baseline low
        add(outcomes, 4, "angle", "investment_roi", 90.0, 50, 0.05);  // strong: broad + high volume
        add(outcomes, 2, "angle", "scarcity", 95.0, 5, 0.05);         // thin: 2 creatives, tiny volume

        AttributeAttribution map = attributor(new Priors(OptionalDouble.empty()))
                .aggregate(CLIENT, RE, null, outcomes);

        AttributeStat strong = map.stat("angle", "investment_roi");
        AttributeStat thin = map.stat("angle", "scarcity");
        assertNotNull(strong);
        assertNotNull(thin);

        // The strong, broad, high-volume value is a confident winner.
        assertTrue(strong.winner(), "broad high-volume value should win");
        assertTrue(strong.confidence() >= AttributeAttributor.WINNER_CONFIDENCE);

        // The thin value's raw mean (95) is regularized DOWN toward the baseline, and it is NOT a winner
        // (low confidence + too few creatives) — "an attribute seen on 2 creatives isn't proven".
        assertFalse(thin.winner(), "thin-evidence value must not be declared a winner");
        assertTrue(thin.regularizedScore() < 90.0, "raw 95 mean should be shrunk well below its observation");
        assertTrue(thin.confidence() < AttributeAttributor.WINNER_CONFIDENCE);
        assertTrue(thin.underExplored(), "thin value should surface as an explore target");
    }

    @Test
    void junkCorrelationPenalty_dropsWinnerStatusAtEqualScore() {

        List<CreativeOutcome> outcomes = new ArrayList<>();
        add(outcomes, 4, "angle", "location", 70.0, 50, 0.05);             // baseline
        add(outcomes, 4, "offer", "pre_launch_price", 88.0, 50, 0.03);     // clean leads
        add(outcomes, 4, "offer", "spot_booking_discount", 88.0, 50, 0.45); // SAME score, junky leads

        AttributeAttribution map = attributor(new Priors(OptionalDouble.empty()))
                .aggregate(CLIENT, RE, null, outcomes);

        AttributeStat clean = map.stat("offer", "pre_launch_price");
        AttributeStat junky = map.stat("offer", "spot_booking_discount");

        // Identical score/volume/breadth -> identical regularized performance ...
        assertEquals(clean.regularizedScore(), junky.regularizedScore(), 1e-6);
        // ... but the junk-correlated value is penalized: higher junk correlation, and it loses the win.
        assertTrue(junky.junkCorrelation() > clean.junkCorrelation());
        assertTrue(junky.junkCorrelation() > AttributeAttributor.WINNER_MAX_JUNK);
        assertTrue(clean.winner(), "clean-lead value should win");
        assertFalse(junky.winner(), "junk-correlated value must be penalized out of winner status");
    }

    @Test
    void coldStart_noRealizedVolume_fallsBackToPriorBaseline() {

        AttributeAttribution withPrior = attributor(new Priors(OptionalDouble.of(62.0)))
                .aggregate(CLIENT, RE, null, List.of());

        assertTrue(withPrior.coldStart());
        assertEquals(62.0d, withPrior.baseline(), 1e-9);
        assertTrue(withPrior.stats().isEmpty(), "no realized outcomes -> no observed stats");
        assertFalse(withPrior.underExplored().isEmpty(), "the whole taxonomy is the explore frontier");

        // With no prior at all, the cold-start anchor is the neutral baseline.
        AttributeAttribution noPrior = attributor(new Priors(OptionalDouble.empty()))
                .aggregate(CLIENT, RE, null, List.of());
        assertTrue(noPrior.coldStart());
        assertEquals(AttributeAttributor.NEUTRAL_BASELINE, noPrior.baseline(), 1e-9);
    }

    @Test
    void exploreExploitSurface_separatesWinnersFromTheUnexploredFrontier() {

        List<CreativeOutcome> outcomes = new ArrayList<>();
        add(outcomes, 4, "angle", "location", 70.0, 50, 0.05);
        add(outcomes, 4, "angle", "investment_roi", 90.0, 50, 0.05);

        AttributeAttribution map = attributor(new Priors(OptionalDouble.empty()))
                .aggregate(CLIENT, RE, null, outcomes);

        // Exploit: the winning value shows up.
        assertTrue(map.winners().stream().anyMatch(s -> "investment_roi".equals(s.value())));

        // Explore: a taxonomy value never tried (angle=lifestyle) is on the frontier with zero evidence.
        AttributeStat frontier = map.underExplored().stream()
                .filter(s -> "angle".equals(s.axis()) && "lifestyle".equals(s.value()))
                .findFirst().orElse(null);
        assertNotNull(frontier, "an untried taxonomy value should appear on the explore frontier");
        assertEquals(0L, frontier.volume());
        assertEquals(0, frontier.creativeCount());
        assertTrue(frontier.underExplored());
    }

    @Test
    void attribute_readsAreScopedToTheClientCode_soTheMapIsTenantPrivate() {

        CreativeScorer scorer = mock(CreativeScorer.class);
        CreativeAttributeDao attrDao = mock(CreativeAttributeDao.class);
        PerformanceSnapshotDao snapDao = mock(PerformanceSnapshotDao.class);
        VerticalRegistry vr = mock(VerticalRegistry.class);
        when(vr.getOrDefault(any())).thenReturn(new RealEstatePlaybook());

        CreativeAttributeRow row = new CreativeAttributeRow()
                .setClientCode(CLIENT).setCreativeId("cr1").setAxis("angle").setValue("investment_roi");
        when(attrDao.findByClient(CLIENT)).thenReturn(List.of(row));
        when(snapDao.findByClient(CLIENT)).thenReturn(List.of());
        when(scorer.score(eq("cr1"), anyList())).thenReturn(CreativeScore.empty("cr1"));

        AttributeAttributor attributor = new AttributeAttributor(scorer, attrDao, snapDao, vr,
                new Priors(OptionalDouble.empty()));

        AttributeAttribution map = attributor.attribute(CLIENT, RE, null);

        // Both source reads are scoped to the caller's client — one tenant's map cannot pull another's rows.
        verify(attrDao).findByClient(CLIENT);
        verify(snapDao).findByClient(CLIENT);
        assertEquals(CLIENT, map.clientCode());
        assertTrue(map.coldStart(), "no realized outcomes -> cold start");
    }
}
