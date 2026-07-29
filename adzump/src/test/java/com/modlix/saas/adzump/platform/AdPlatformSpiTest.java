package com.modlix.saas.adzump.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.modlix.saas.adzump.enums.CampaignType;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.CampaignPlanBody;
import com.modlix.saas.adzump.model.Objective;
import com.modlix.saas.adzump.model.leadzump.Grain;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.commons2.exception.GenericException;

/**
 * Offline contract tests for the J2b SPI seam, run entirely against {@link NoopPlatform} — no
 * network, no SDK. Covers the P1 exit: the registry resolves registered platforms and throws for
 * absent ones, and a {@code CompiledCampaign} round-trips compile -> launch -> insights.
 */
class AdPlatformSpiTest {

    private static final AdzumpMessageResourceService MSG = new AdzumpMessageResourceService();

    private static CampaignPlan samplePlan(Platform platform, CampaignType type) {
        return new CampaignPlan()
                .setName("Noop Plan")
                .setCampaignTypes(Map.of(platform, type))
                .setBody(new CampaignPlanBody().setObjective(new Objective()));
    }

    private static Token token() {
        return new Token("noop-token", "act_noop_1", null, Map.of());
    }

    @Test
    void registryResolvesRegisteredPlatformsAndReportsAvailability() {

        NoopPlatform meta = new NoopPlatform(Platform.META, false);
        NoopPlatform google = new NoopPlatform(Platform.GOOGLE, false);

        AdPlatformRegistry registry = new AdPlatformRegistry(List.of(meta, google), MSG);

        assertSame(meta, registry.get(Platform.META));
        assertSame(google, registry.get(Platform.GOOGLE));
        assertEquals(2, registry.available().size());
        assertTrue(registry.available().contains(Platform.META));
        assertTrue(registry.available().contains(Platform.GOOGLE));
    }

    @Test
    void registryThrowsWhenPlatformNotRegistered() {

        AdPlatformRegistry registry = new AdPlatformRegistry(List.of(new NoopPlatform(Platform.META, false)), MSG);

        GenericException ex = assertThrows(GenericException.class, () -> registry.get(Platform.GOOGLE));
        assertNotNull(ex.getMessage());
        assertTrue(registry.available().contains(Platform.META));
        assertFalse(registry.available().contains(Platform.GOOGLE));
    }

    @Test
    void compileLaunchInsightsRoundTripsOnMeta() {

        NoopPlatform meta = new NoopPlatform(Platform.META, false);
        AdPlatform platform = meta;

        // compile (pure; effective config passed by J7 — null here, the noop compiler ignores it)
        CampaignPlan plan = samplePlan(Platform.META, CampaignType.LEADS);
        CompiledCampaign compiled = platform.compiler().compile(plan, null);

        assertEquals(Platform.META, compiled.platform());
        assertEquals(CampaignType.LEADS, compiled.type());
        assertEquals("Noop Plan", compiled.name());
        assertNotNull(compiled.payload());
        assertEquals(1, meta.getCompiledPlans().size());

        // launch (paused) -> platform ids come back on the Links fragment for J8 to persist
        LaunchResult result = platform.launchPaused(compiled, token());
        assertTrue(result.ok());
        assertNull(result.error());
        assertNotNull(result.links());
        assertNotNull(result.links().getMeta());
        assertEquals("noop-meta-campaign-1", result.links().getMeta().getCampaignId());
        assertEquals(1, meta.getLaunchedCampaigns().size());

        // insights honor the requested grain
        InsightQuery query = new InsightQuery("act_noop_1", List.of("noop-meta-campaign-1"),
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), Grain.CAMPAIGN, "Asia/Kolkata");
        List<PlatformInsight> insights = platform.insights(token(), query);

        assertFalse(insights.isEmpty());
        assertEquals(Grain.CAMPAIGN, insights.getFirst().grain());
        assertEquals("noop-meta-campaign-1", insights.getFirst().ref().id());
        assertNotNull(insights.getFirst().spend());
    }

    @Test
    void failModeLaunchYieldsFailedResultForPartialFailureHandling() {

        NoopPlatform google = new NoopPlatform(Platform.GOOGLE, true);

        CompiledCampaign compiled = google.compiler()
                .compile(samplePlan(Platform.GOOGLE, CampaignType.SEARCH), null);

        LaunchResult result = google.launchPaused(compiled, token());

        assertFalse(result.ok());
        assertNotNull(result.error());
        assertNull(result.links());
        assertEquals(Platform.GOOGLE, result.platform());
    }

    @Test
    void capabilitiesAreDataDrivenPerType() {

        NoopPlatform google = new NoopPlatform(Platform.GOOGLE, false);
        PlatformCapabilities caps = google.capabilities();

        assertTrue(caps.supports(CampaignType.SEARCH));
        assertTrue(caps.supports(CampaignType.PMAX));

        OptimizationProfile search = caps.optimizationFor(CampaignType.SEARCH);
        OptimizationProfile pmax = caps.optimizationFor(CampaignType.PMAX);

        // Transparent Search exposes surgical keyword control at the AD grain...
        assertTrue(search.allows(Lever.KEYWORD));
        assertEquals(Grain.AD, search.finestReportingGrain());

        // ...while opaque PMax does not, and reports at the CAMPAIGN grain.
        assertFalse(pmax.allows(Lever.KEYWORD));
        assertTrue(pmax.allows(Lever.BUDGET));
        assertEquals(Grain.CAMPAIGN, pmax.finestReportingGrain());
    }

    @Test
    void mutationsAndDiscoveryAreRecordedAndGated() {

        NoopPlatform meta = new NoopPlatform(Platform.META, false);
        Token t = token();

        meta.setStatus(t, new PlatformRef("campaign", "c1"), RunState.PAUSE);
        assertEquals(1, meta.getStatusCalls().size());
        assertEquals(RunState.PAUSE, meta.getStatusCalls().getFirst().state());

        // Meta exposes pages; keyword ideas are Google-only.
        assertFalse(meta.pages(t, "act_noop_1").isEmpty());
        assertTrue(meta.searchKeywords(t, new KeywordSeed(List.of("seed"), null)).isEmpty());

        NoopPlatform google = new NoopPlatform(Platform.GOOGLE, false);
        assertTrue(google.pages(t, "act_noop_1").isEmpty());
        assertFalse(google.searchKeywords(t, new KeywordSeed(List.of("seed"), null)).isEmpty());
    }
}
