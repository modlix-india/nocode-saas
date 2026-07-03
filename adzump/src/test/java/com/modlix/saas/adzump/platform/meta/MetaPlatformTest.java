package com.modlix.saas.adzump.platform.meta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.modlix.saas.adzump.compile.MetaCompiler;
import com.modlix.saas.adzump.compile.MetaLeadsCompiler;
import com.modlix.saas.adzump.enums.CampaignType;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.model.leadzump.Grain;
import com.modlix.saas.adzump.platform.AdAccountRef;
import com.modlix.saas.adzump.platform.AudienceRef;
import com.modlix.saas.adzump.platform.GeoRef;
import com.modlix.saas.adzump.platform.KeywordSeed;
import com.modlix.saas.adzump.platform.Lever;
import com.modlix.saas.adzump.platform.OptimizationProfile;
import com.modlix.saas.adzump.platform.PixelRef;
import com.modlix.saas.adzump.platform.PlatformCapabilities;
import com.modlix.saas.adzump.platform.Token;

/**
 * Offline tests for the Meta {@link com.modlix.saas.adzump.platform.AdPlatform} — {@link MetaGraphClient}
 * mocked. Verifies the SPI surface: the platform reports {@code META}, its data-driven capabilities,
 * the discovery reads mapped to platform-real refs, the capability-gated empty keyword search, and
 * that {@link MetaPlatform#compiler()} hands back the injected J7 compiler.
 */
class MetaPlatformTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final MetaCompiler COMPILER = new MetaCompiler(new MetaLeadsCompiler());

    private static Token token() {
        return new Token("tok", "act_123456", null, Map.of());
    }

    private static MetaPlatform platform(MetaGraphClient graph) {
        return new MetaPlatform(graph, COMPILER, mock(MetaLifecycle.class), mock(MetaInsightsReader.class));
    }

    private static JsonNode json(String raw) {
        try {
            return MAPPER.readTree(raw);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void reportsMetaAndReturnsTheInjectedCompiler() {
        MetaPlatform platform = platform(mock(MetaGraphClient.class));
        assertEquals(Platform.META, platform.code());
        assertSame(COMPILER, platform.compiler());
    }

    @Test
    void capabilitiesAreDataDrivenPerType() {
        PlatformCapabilities caps = platform(mock(MetaGraphClient.class)).capabilities();

        assertTrue(caps.supports(CampaignType.LEADS));
        assertTrue(caps.supports(CampaignType.ADVANTAGE_PLUS));

        OptimizationProfile leads = caps.optimizationFor(CampaignType.LEADS);
        assertEquals(Grain.AD, leads.finestReportingGrain());
        assertTrue(leads.allows(Lever.BID));
        assertTrue(leads.allows(Lever.CREATIVE_ROTATE));

        // Advantage+ is the opaque/automated type: thin levers, campaign-grain reporting.
        OptimizationProfile advantage = caps.optimizationFor(CampaignType.ADVANTAGE_PLUS);
        assertEquals(Grain.CAMPAIGN, advantage.finestReportingGrain());
        assertTrue(advantage.allows(Lever.BUDGET));
        assertFalse(advantage.allows(Lever.BID));
    }

    @Test
    void accountsMergeOwnedAndClientAdAccountsAcrossBusinesses() {
        MetaGraphClient graph = mock(MetaGraphClient.class);
        when(graph.get(any(), eq("me/businesses"), any()))
                .thenReturn(json("{\"data\":[{\"id\":\"biz_1\",\"name\":\"Acme\"}]}"));
        when(graph.get(any(), eq("biz_1/owned_ad_accounts"), any()))
                .thenReturn(json("{\"data\":[{\"id\":\"act_1\",\"account_id\":\"1\",\"name\":\"Owned\",\"currency\":\"USD\"}]}"));
        // No "id" field → the ref id is derived as act_<account_id>.
        when(graph.get(any(), eq("biz_1/client_ad_accounts"), any()))
                .thenReturn(json("{\"data\":[{\"account_id\":\"2\",\"name\":\"Client\",\"currency\":\"INR\"}]}"));

        List<AdAccountRef> accounts = platform(graph).accounts(token());

        assertEquals(2, accounts.size());
        assertEquals("act_1", accounts.get(0).id());
        assertEquals("USD", accounts.get(0).currency());
        assertEquals("act_2", accounts.get(1).id());
        assertEquals("INR", accounts.get(1).currency());
    }

    @Test
    void pixelsMapFromAccountAdsPixels() {
        MetaGraphClient graph = mock(MetaGraphClient.class);
        when(graph.get(any(), eq("act_123456/adspixels"), any()))
                .thenReturn(json("{\"data\":[{\"id\":\"px_1\",\"name\":\"Site Pixel\"}]}"));

        List<PixelRef> pixels = platform(graph).pixels(token(), "act_123456");
        assertEquals(1, pixels.size());
        assertEquals("px_1", pixels.getFirst().id());
        assertEquals("Site Pixel", pixels.getFirst().name());
    }

    @Test
    void searchGeoMapsMetaTypeToNeutralVocabulary() {
        MetaGraphClient graph = mock(MetaGraphClient.class);
        when(graph.get(any(), eq("search"), any()))
                .thenReturn(json("{\"data\":[{\"key\":\"1001\",\"name\":\"Whitefield\",\"type\":\"city\"}]}"));

        List<GeoRef> geos = platform(graph).searchGeo(token(), "Whitefield");
        assertEquals(1, geos.size());
        assertEquals("1001", geos.getFirst().id());
        assertEquals("CITY", geos.getFirst().type());
    }

    @Test
    void searchInterestsMapToAudienceRefs() {
        MetaGraphClient graph = mock(MetaGraphClient.class);
        when(graph.get(any(), eq("search"), any()))
                .thenReturn(json("{\"data\":[{\"id\":\"6003629266583\",\"name\":\"Real estate\"}]}"));

        List<AudienceRef> interests = platform(graph).searchInterests(token(), "real estate");
        assertEquals(1, interests.size());
        assertEquals("6003629266583", interests.getFirst().id());
        assertEquals("INTEREST", interests.getFirst().type());
    }

    @Test
    void keywordSearchIsEmptyAndMakesNoGraphCall() {
        MetaGraphClient graph = mock(MetaGraphClient.class);
        MetaPlatform platform = platform(graph);

        assertTrue(platform.searchKeywords(token(), new KeywordSeed(List.of("2 bhk"), null)).isEmpty());
        // Capability-gated: Meta has no keyword surface, so no Graph call is made.
        verifyNoInteractions(graph);
    }
}
