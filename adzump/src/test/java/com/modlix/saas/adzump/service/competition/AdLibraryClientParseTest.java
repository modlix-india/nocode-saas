package com.modlix.saas.adzump.service.competition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.model.competition.CompetitorAd;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;

/**
 * Offline unit tests for the {@code ads_archive} wire mapping in {@link AdLibraryClient#parseAds} — dates
 * (plain {@code YYYY-MM-DD} and offset date-times), the reach-only-for-political rule, first-of-array
 * creative text, and skipping malformed nodes. No HTTP: {@code parseAds} is exercised on synthetic JSON,
 * which is why it is a distinct seam from the live call (proven at the P4.5 gate).
 */
class AdLibraryClientParseTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AdLibraryClient client = new AdLibraryClient(
            "https://graph.facebook.com", "v22.0", "IN", new AdzumpMessageResourceService());

    private static JsonNode json(String s) {
        try {
            return MAPPER.readTree(s);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void parsesAdsMappingDatesReachAndCreativeText() {

        JsonNode data = json("""
                [
                  {
                    "id": "1",
                    "page_id": "P1",
                    "page_name": "Builder A",
                    "ad_creative_bodies": ["", "Assured ROI, book your site visit"],
                    "ad_creative_link_titles": ["Premium Apartments"],
                    "ad_snapshot_url": "https://www.facebook.com/ads/archive/render_ad/?id=1",
                    "ad_delivery_start_time": "2026-01-01",
                    "impressions": { "lower_bound": "1000", "upper_bound": "5000" }
                  },
                  {
                    "id": "2",
                    "page_id": "P2",
                    "page_name": "Builder B",
                    "ad_creative_bodies": [],
                    "ad_delivery_start_time": "2026-02-01T07:00:00+0000",
                    "ad_delivery_stop_time": "2026-03-15T07:00:00+0000"
                  },
                  "not-an-object"
                ]
                """);

        List<CompetitorAd> ads = this.client.parseAds(data, Platform.META);

        assertEquals(2, ads.size(), "malformed non-object entry is skipped, not thrown");

        CompetitorAd a1 = ads.getFirst();
        assertEquals("1", a1.getId());
        assertEquals("P1", a1.getPageId());
        assertEquals("Builder A", a1.getPageName());
        assertEquals("Assured ROI, book your site visit", a1.getCreativeBody()); // first non-blank
        assertEquals("Premium Apartments", a1.getCreativeTitle());
        assertEquals(LocalDate.of(2026, 1, 1), a1.getDeliveryStart());
        assertNull(a1.getDeliveryStop(), "no stop time => still running");
        assertEquals(5000L, a1.getReach(), "reach comes from impressions upper_bound when present");
        assertEquals(Platform.META, a1.getPlatform());

        CompetitorAd a2 = ads.get(1);
        assertEquals(LocalDate.of(2026, 2, 1), a2.getDeliveryStart(), "offset date-time parsed to date");
        assertEquals(LocalDate.of(2026, 3, 15), a2.getDeliveryStop());
        assertNull(a2.getReach(), "commercial ad without impressions => no reach");
        assertNull(a2.getCreativeBody(), "empty creative-bodies array => null body");
    }

    @Test
    void parseDateHandlesTheKnownForms() {
        assertEquals(LocalDate.of(2026, 1, 1), AdLibraryClient.parseDate("2026-01-01"));
        assertEquals(LocalDate.of(2026, 2, 1), AdLibraryClient.parseDate("2026-02-01T07:00:00+0000"));
        assertNull(AdLibraryClient.parseDate(null));
        assertNull(AdLibraryClient.parseDate("  "));
        assertNull(AdLibraryClient.parseDate("garbage"));
    }

    @Test
    void emptyOrNonArrayDataYieldsEmptyList() {
        assertEquals(0, this.client.parseAds(json("{}"), Platform.META).size());
        assertEquals(0, this.client.parseAds(json("[]"), Platform.META).size());
        assertEquals(0, this.client.parseAds(null, Platform.META).size());
    }
}
