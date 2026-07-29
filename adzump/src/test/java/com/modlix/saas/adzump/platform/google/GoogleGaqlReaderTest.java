package com.modlix.saas.adzump.platform.google;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.ads.googleads.v24.common.Metrics;
import com.google.ads.googleads.v24.common.Segments;
import com.google.ads.googleads.v24.resources.Campaign;
import com.google.ads.googleads.v24.resources.Customer;
import com.google.ads.googleads.v24.services.GoogleAdsRow;
import com.modlix.saas.adzump.model.leadzump.Grain;
import com.modlix.saas.adzump.platform.InsightQuery;
import com.modlix.saas.adzump.platform.PlatformInsight;
import com.modlix.saas.adzump.platform.Token;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;

/**
 * Offline test of {@link GoogleGaqlReader}: the centralized grain→GAQL-resource map, the query the
 * reader builds per grain, and the row→{@link PlatformInsight} parsing (cost micros → major units,
 * account currency, platform conversions) against a canned {@link GoogleAdsRow} through a mocked
 * {@link GoogleAdsClientFacade}. No network / no gRPC.
 */
class GoogleGaqlReaderTest {

    private static final String CUSTOMER = "9846007422";

    private final AdzumpMessageResourceService msg = new AdzumpMessageResourceService();

    private static Token token() {
        return new Token("oauth-access-token", "984-600-7422", "1234567890", Map.of());
    }

    private static InsightQuery query(Grain grain, List<String> ids) {
        return new InsightQuery(CUSTOMER, ids, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                grain, "Asia/Kolkata");
    }

    @Test
    void grainToResourceMapIsCorrect() {
        assertEquals("campaign", GoogleGaqlReader.resourceForGrain(Grain.CAMPAIGN));
        assertEquals("ad_group", GoogleGaqlReader.resourceForGrain(Grain.ADSET));
        assertEquals("ad_group_ad", GoogleGaqlReader.resourceForGrain(Grain.AD));
    }

    @Test
    void buildQueryUsesTheGrainResourceDateRangeAndIdFilter() {
        GoogleGaqlReader reader = new GoogleGaqlReader(mock(GoogleAdsClientFacade.class), this.msg);

        String campaignGaql = reader.buildQuery(query(Grain.CAMPAIGN, List.of()));
        assertTrue(campaignGaql.contains("FROM campaign"), campaignGaql);
        assertTrue(campaignGaql.contains("segments.date BETWEEN '2026-06-01' AND '2026-06-30'"), campaignGaql);
        assertTrue(campaignGaql.contains("metrics.cost_micros"), campaignGaql);

        String adGaql = reader.buildQuery(query(Grain.AD, List.of("555")));
        assertTrue(adGaql.contains("FROM ad_group_ad"), adGaql);
        assertTrue(adGaql.contains("ad_group_ad.ad.id IN (555)"), adGaql);

        String adSetGaql = reader.buildQuery(query(Grain.ADSET, List.of("777", "888")));
        assertTrue(adSetGaql.contains("FROM ad_group"), adSetGaql);
        assertTrue(adSetGaql.contains("ad_group.id IN (777, 888)"), adSetGaql);
    }

    @Test
    void insightsParseMetricsMicrosAndCurrency() {
        GoogleAdsClientFacade facade = mock(GoogleAdsClientFacade.class);
        GoogleGaqlReader reader = new GoogleGaqlReader(facade, this.msg);
        Token token = token();

        GoogleAdsRow row = GoogleAdsRow.newBuilder()
                .setCampaign(Campaign.newBuilder().setId(555L))
                .setCustomer(Customer.newBuilder().setCurrencyCode("INR"))
                .setMetrics(Metrics.newBuilder()
                        .setImpressions(1000L).setClicks(50L).setCostMicros(12_340_000L).setConversions(5))
                .setSegments(Segments.newBuilder().setDate("2026-06-01"))
                .build();

        when(facade.search(eq(token), eq(CUSTOMER), anyString())).thenReturn(List.of(row));

        List<PlatformInsight> insights = reader.insights(token, query(Grain.CAMPAIGN, List.of("555")));

        assertEquals(1, insights.size());
        PlatformInsight insight = insights.getFirst();
        assertEquals(Grain.CAMPAIGN, insight.grain());
        assertEquals("campaign", insight.ref().type());
        assertEquals("555", insight.ref().id());
        assertEquals(1000L, insight.impressions());
        assertEquals(50L, insight.clicks());
        assertEquals(5L, insight.platformConversions());
        assertEquals("INR", insight.spend().getCurrency());
        assertEquals(0, insight.spend().getAmount().compareTo(new BigDecimal("12.34"))); // micros → major units
    }
}
