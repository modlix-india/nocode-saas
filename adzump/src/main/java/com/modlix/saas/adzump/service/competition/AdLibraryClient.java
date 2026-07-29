package com.modlix.saas.adzump.service.competition;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.model.competition.AdLibraryQuery;
import com.modlix.saas.adzump.model.competition.CompetitorAd;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.commons2.exception.GenericException;

/**
 * J19 — the thin blocking HTTP facade over the Meta Ad Library {@code ads_archive} API. It knows the
 * wire (endpoint, params, paging cursors, the {@code ads_archive} field shapes) and returns
 * platform-neutral {@link CompetitorAd}s; everything above it (the proxy, the service) works in those
 * and never sees a Graph JSON shape. That seam is the mock point: J19's unit tests <b>mock this
 * client</b> and feed synthetic ads, so no test touches a live account.
 *
 * <p><b>Auth</b>: the access token (resolved by J2 {@code ConnectionService.resolve(Platform.META)} and
 * passed in — this facade never fetches its own credentials, mirroring {@link com.modlix.saas.adzump.platform.meta.MetaGraphClient})
 * rides in the {@code Authorization: Bearer} header, so it is never placed in a logged URL. <b>Version</b>
 * reuses the pinned {@code adzump.meta.*} config. <b>Errors</b>: any 4xx/5xx maps to a
 * {@link GenericException} via {@link AdzumpMessageResourceService#META_API_ERROR}.
 *
 * <p><b>What the Ad Library does and doesn't give</b> (J19 §5.1): the creative, advertiser page, delivery
 * start/stop and snapshot URL for every ad; reach/impressions <b>only for political/issue ads</b>. This
 * facade surfaces reach when present and leaves it null otherwise — it never fabricates a metric.
 *
 * <p><b>Live path</b>: the outbound calls fire only against a real connected Meta account (deferred to
 * the P4.5 integration gate). The code here is the real implementation; every unit test mocks it. Open
 * question (J19 §9): whether {@code ads_archive} at our volume needs the public Ad Library API token vs
 * the account's Meta connection — resolved at the live gate.
 */
@Component
public class AdLibraryClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** The one edge this facade hits. */
    private static final String ADS_ARCHIVE = "ads_archive";

    /** Fields we request per ad (commercial ads omit reach; it stays null then). */
    private static final String FIELDS = String.join(",",
            "id", "page_id", "page_name",
            "ad_creative_bodies", "ad_creative_link_titles", "ad_creative_link_descriptions",
            "ad_snapshot_url", "ad_delivery_start_time", "ad_delivery_stop_time",
            "impressions", "publisher_platforms");

    private final RestClient http;
    private final List<String> defaultCountries;
    private final AdzumpMessageResourceService msgService;

    public AdLibraryClient(
            @Value("${adzump.meta.base-url:https://graph.facebook.com}") String baseUrl,
            @Value("${adzump.meta.api-version:v22.0}") String apiVersion,
            @Value("${adzump.adlibrary.default-countries:IN}") String defaultCountriesCsv,
            AdzumpMessageResourceService msgService) {

        this.msgService = msgService;
        this.defaultCountries = splitCsv(defaultCountriesCsv);
        String root = stripTrailingSlash(baseUrl) + "/" + apiVersion;
        this.http = RestClient.builder().baseUrl(root).build();
    }

    // --- public search API (the mock seam) ------------------------------------------------------

    /**
     * All ads for a single advertiser page (via {@code search_page_ids}), paged up to
     * {@link AdLibraryQuery#maxPages()}. Used to pull a known competitor's running ads.
     */
    public List<CompetitorAd> searchByAdvertiser(String accessToken, String advertiserPageId, AdLibraryQuery query) {
        return search(accessToken, Map.of("search_page_ids", jsonArray(List.of(advertiserPageId))), query);
    }

    /**
     * Ads matching free-text keywords / a category (via {@code search_terms}), across advertisers — the
     * market-level (non-advertiser) search that feeds breadth. Used when A2 hands keywords rather than a
     * specific competitor.
     */
    public List<CompetitorAd> searchByKeyword(String accessToken, String searchTerms, AdLibraryQuery query) {
        return search(accessToken, Map.of("search_terms", searchTerms), query);
    }

    // --- paging + request -----------------------------------------------------------------------

    private List<CompetitorAd> search(String accessToken, Map<String, String> selector, AdLibraryQuery query) {

        AdLibraryQuery q = query == null ? AdLibraryQuery.runningIn(this.defaultCountries) : query;
        List<String> countries = q.reachedCountries().isEmpty() ? this.defaultCountries : q.reachedCountries();

        List<CompetitorAd> out = new ArrayList<>();
        String after = null;

        for (int page = 0; page < q.maxPages(); page++) {

            final String cursor = after;
            JsonNode root = get(accessToken, builder -> {
                builder.path("/" + ADS_ARCHIVE);
                builder.queryParam("ad_reached_countries", jsonArray(countries));
                builder.queryParam("ad_active_status", q.activeStatus());
                builder.queryParam("ad_type", "ALL");
                builder.queryParam("fields", FIELDS);
                builder.queryParam("limit", Integer.toString(q.pageSize()));
                selector.forEach(builder::queryParam);
                if (cursor != null)
                    builder.queryParam("after", cursor);
            });

            JsonNode data = root == null ? null : root.get("data");
            if (data == null || !data.isArray() || data.isEmpty())
                break;

            out.addAll(parseAds(data, Platform.META));

            after = nextCursor(root);
            if (after == null)
                break;
        }

        return out;
    }

    private JsonNode get(String accessToken, java.util.function.Consumer<org.springframework.web.util.UriBuilder> uri) {
        try {
            return this.http.get()
                    .uri(builder -> {
                        uri.accept(builder);
                        return builder.build();
                    })
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + (accessToken == null ? "" : accessToken))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw apiError(e);
        } catch (RestClientException e) {
            throw transportError(e);
        }
    }

    // --- parsing (package-private: unit-tested on synthetic JSON, no HTTP) -----------------------

    /**
     * Maps an {@code ads_archive} {@code data} array into {@link CompetitorAd}s. Package-private so the
     * wire mapping (dates, the reach-only-for-political rule, first-of-array creative text) is unit-tested
     * without a live call. Malformed / empty nodes are skipped, not thrown, so one bad ad never fails the
     * whole page.
     */
    List<CompetitorAd> parseAds(JsonNode data, Platform platform) {

        List<CompetitorAd> ads = new ArrayList<>();
        if (data == null || !data.isArray())
            return ads;

        for (JsonNode node : data) {
            if (node == null || !node.isObject())
                continue;

            CompetitorAd ad = new CompetitorAd()
                    .setPlatform(platform)
                    .setId(text(node, "id"))
                    .setPageId(text(node, "page_id"))
                    .setPageName(text(node, "page_name"))
                    .setCreativeBody(firstOfArray(node, "ad_creative_bodies"))
                    .setCreativeTitle(firstOfArray(node, "ad_creative_link_titles"))
                    .setAdSnapshotUrl(text(node, "ad_snapshot_url"))
                    .setDeliveryStart(parseDate(text(node, "ad_delivery_start_time")))
                    .setDeliveryStop(parseDate(text(node, "ad_delivery_stop_time")))
                    .setReach(reachOf(node));

            ads.add(ad);
        }

        return ads;
    }

    /** Reach proxy from the {@code impressions} range — present only for political/issue ads, else null. */
    private static Long reachOf(JsonNode node) {
        JsonNode impressions = node.get("impressions");
        if (impressions == null || !impressions.isObject())
            return null;
        Long upper = longOf(impressions.get("upper_bound"));
        if (upper != null)
            return upper;
        return longOf(impressions.get("lower_bound"));
    }

    /**
     * A long from a numeric or string-encoded JSON node. The Ad Library returns {@code impressions}
     * bounds as quoted strings ({@code "5000"}), so a plain {@code canConvertToLong()} (numeric-only)
     * misses them; this accepts both and returns null for absent / blank / non-numeric values.
     */
    private static Long longOf(JsonNode n) {
        if (n == null || n.isNull())
            return null;
        if (n.canConvertToLong())
            return n.asLong();
        if (n.isTextual()) {
            String s = n.asText().trim();
            if (!s.isEmpty()) {
                try {
                    return Long.parseLong(s);
                } catch (NumberFormatException ignored) {
                    // not a numeric string
                }
            }
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull())
            return null;
        String s = v.asText();
        return s == null || s.isBlank() ? null : s;
    }

    private static String firstOfArray(JsonNode node, String field) {
        JsonNode arr = node.get(field);
        if (arr == null || !arr.isArray())
            return null;
        for (JsonNode e : arr) {
            if (e != null && !e.isNull()) {
                String s = e.asText();
                if (s != null && !s.isBlank())
                    return s;
            }
        }
        return null;
    }

    /**
     * Parses an Ad Library delivery timestamp to a {@link LocalDate}. The API has returned both plain
     * {@code YYYY-MM-DD} and offset date-times across versions, so we try the offset form, then the plain
     * date, then a 10-char prefix; unparseable / blank yields null.
     */
    static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank())
            return null;
        String s = raw.trim();
        try {
            return OffsetDateTime.parse(s).toLocalDate();
        } catch (RuntimeException ignored) {
            // not an offset date-time
        }
        try {
            return LocalDate.parse(s);
        } catch (RuntimeException ignored) {
            // not a plain date
        }
        if (s.length() >= 10) {
            try {
                return LocalDate.parse(s.substring(0, 10));
            } catch (RuntimeException ignored) {
                // give up
            }
        }
        return null;
    }

    private static String nextCursor(JsonNode root) {
        JsonNode paging = root.get("paging");
        if (paging == null)
            return null;
        JsonNode cursors = paging.get("cursors");
        if (cursors == null)
            return null;
        JsonNode after = cursors.get("after");
        return after == null || after.isNull() ? null : after.asText();
    }

    // --- helpers --------------------------------------------------------------------------------

    private static List<String> splitCsv(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null)
            return out;
        for (String part : csv.split(",")) {
            String t = part.trim();
            if (!t.isEmpty())
                out.add(t);
        }
        return out;
    }

    /** Encodes a list as the JSON-array string Graph expects for array params, e.g. {@code ["IN"]}. */
    private static String jsonArray(List<String> values) {
        try {
            return MAPPER.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not encode Ad Library array param", e);
        }
    }

    private static String stripTrailingSlash(String s) {
        return s != null && s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private GenericException apiError(RestClientResponseException e) {
        return this.msgService.nonReactiveMessage(
                msg -> new GenericException(HttpStatus.BAD_GATEWAY, msg, e),
                AdzumpMessageResourceService.META_API_ERROR, extractMetaError(e));
    }

    private GenericException transportError(RestClientException e) {
        return this.msgService.nonReactiveMessage(
                msg -> new GenericException(HttpStatus.BAD_GATEWAY, msg, e),
                AdzumpMessageResourceService.META_API_ERROR,
                e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    }

    private static String extractMetaError(RestClientResponseException e) {
        String raw = e.getResponseBodyAsString();
        if (raw != null && !raw.isBlank()) {
            try {
                JsonNode error = MAPPER.readTree(raw).get("error");
                if (error != null && error.get("message") != null)
                    return error.get("message").asText();
            } catch (JsonProcessingException ignored) {
                // fall through to raw body
            }
            return raw;
        }
        return e.getStatusText();
    }
}
