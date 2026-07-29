package com.modlix.saas.adzump.platform.meta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.modlix.saas.adzump.model.leadzump.Grain;
import com.modlix.saas.adzump.platform.InsightQuery;
import com.modlix.saas.adzump.platform.PlatformInsight;
import com.modlix.saas.adzump.platform.Token;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;

/**
 * Offline tests for the Meta Insights reader — {@link MetaGraphClient} mocked. The point is the grain
 * gotcha: the reader must set {@code level} to the grain AND request the matching id fields
 * ({@code campaign_id}/{@code adset_id}/{@code ad_id}) so finer rows do not come back zero. Also
 * verifies the account-currency spend + lead-action parsing.
 */
class MetaInsightsReaderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AdzumpMessageResourceService MSG = new AdzumpMessageResourceService();

    private static Token token() {
        return new Token("tok", "act_123456", null, Map.of());
    }

    private static JsonNode json(String raw) {
        try {
            return MAPPER.readTree(raw);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void adGrainSetsLevelAndAllIdFieldsAndParsesRow() {

        MetaGraphClient graph = mock(MetaGraphClient.class);
        when(graph.get(any(), anyString(), any())).thenReturn(json("""
                {"data":[{
                  "impressions":"1000","clicks":"50","spend":"12.34","account_currency":"USD",
                  "campaign_id":"camp_1","adset_id":"adset_1","ad_id":"ad_1",
                  "actions":[
                    {"action_type":"post_engagement","value":"70"},
                    {"action_type":"onsite_conversion.lead_grouped","value":"5"},
                    {"action_type":"lead","value":"3"}
                  ]
                }]}"""));

        MetaInsightsReader reader = new MetaInsightsReader(graph, MSG);

        InsightQuery query = new InsightQuery("act_123456", List.of("ad_1"),
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), Grain.AD, "Asia/Kolkata");
        List<PlatformInsight> rows = reader.insights(token(), query);

        // The reader hits the account insights edge with the grain-scoped params.
        ArgumentCaptor<Map> paramCap = ArgumentCaptor.forClass(Map.class);
        verify(graph).get(any(), eq("act_123456/insights"), paramCap.capture());
        Map<?, ?> params = paramCap.getValue();

        assertEquals("ad", params.get("level"));
        String fields = (String) params.get("fields");
        // The gotcha: all three id fields explicit at the AD grain, else adset/ad rows return zero.
        assertTrue(fields.contains("campaign_id"));
        assertTrue(fields.contains("adset_id"));
        assertTrue(fields.contains("ad_id"));
        assertTrue(fields.contains("spend") && fields.contains("actions"));
        // Date range passed through; ids scoped via a filtering clause at the ad grain.
        assertTrue(((String) params.get("time_range")).contains("2026-06-01"));
        assertTrue(((String) params.get("filtering")).contains("ad.id"));

        assertEquals(1, rows.size());
        PlatformInsight row = rows.getFirst();
        assertEquals(Grain.AD, row.grain());
        assertEquals("ad", row.ref().type());
        assertEquals("ad_1", row.ref().id());
        assertEquals(1000L, row.impressions());
        assertEquals(50L, row.clicks());
        assertEquals(0, new BigDecimal("12.34").compareTo(row.spend().getAmount()));
        assertEquals("USD", row.spend().getCurrency());
        // Platform-attributed leads: sum of the lead-typed actions (5 + 3), engagement excluded.
        assertEquals(8L, row.platformConversions());
    }

    @Test
    void campaignGrainOmitsFinerIdFieldsAndSkipsFilteringWhenNoIds() {

        MetaGraphClient graph = mock(MetaGraphClient.class);
        when(graph.get(any(), anyString(), any())).thenReturn(json("""
                {"data":[{"impressions":"10","clicks":"2","spend":"1.00","account_currency":"INR",
                  "campaign_id":"camp_1"}]}"""));

        MetaInsightsReader reader = new MetaInsightsReader(graph, MSG);

        InsightQuery query = new InsightQuery("act_123456", List.of(),
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), Grain.CAMPAIGN, "Asia/Kolkata");
        List<PlatformInsight> rows = reader.insights(token(), query);

        ArgumentCaptor<Map> paramCap = ArgumentCaptor.forClass(Map.class);
        verify(graph).get(any(), eq("act_123456/insights"), paramCap.capture());
        Map<?, ?> params = paramCap.getValue();

        assertEquals("campaign", params.get("level"));
        String fields = (String) params.get("fields");
        assertTrue(fields.contains("campaign_id"));
        assertFalse(fields.contains("adset_id"));
        assertFalse(fields.contains("ad_id"));
        // Account-wide (no ids) → no filtering clause.
        assertNull(params.get("filtering"));

        assertEquals("camp_1", rows.getFirst().ref().id());
        assertEquals("campaign", rows.getFirst().ref().type());
    }

    @Test
    void adsetGrainAddsAdsetIdButNotAdId() {

        MetaGraphClient graph = mock(MetaGraphClient.class);
        when(graph.get(any(), anyString(), any())).thenReturn(json("{\"data\":[]}"));

        MetaInsightsReader reader = new MetaInsightsReader(graph, MSG);

        InsightQuery query = new InsightQuery("act_123456", List.of("adset_1"),
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), Grain.ADSET, "Asia/Kolkata");
        reader.insights(token(), query);

        ArgumentCaptor<Map> paramCap = ArgumentCaptor.forClass(Map.class);
        verify(graph).get(any(), eq("act_123456/insights"), paramCap.capture());
        Map<?, ?> params = paramCap.getValue();

        assertEquals("adset", params.get("level"));
        String fields = (String) params.get("fields");
        assertTrue(fields.contains("adset_id"));
        assertTrue(fields.contains("campaign_id"));
        assertFalse(fields.contains("ad_id"));
        assertTrue(((String) params.get("filtering")).contains("adset.id"));
    }
}
