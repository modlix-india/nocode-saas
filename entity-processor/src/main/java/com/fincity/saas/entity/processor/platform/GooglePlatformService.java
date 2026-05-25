package com.fincity.saas.entity.processor.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincity.saas.entity.processor.dto.CampaignDetails;
import com.fincity.saas.entity.processor.util.GoogleEntityUtil;
import com.fincity.saas.entity.processor.dto.Campaign;
import com.fincity.saas.entity.processor.dto.CampaignMetric;
import com.fincity.saas.entity.processor.enums.CampaignPlatform;
import com.fincity.saas.entity.processor.model.discovery.DiscoveredAd;
import com.fincity.saas.entity.processor.model.discovery.DiscoveredAdset;
import com.fincity.saas.entity.processor.model.discovery.DiscoveredCampaign;
import com.fincity.saas.entity.processor.service.commons.AbstractConnectionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class GooglePlatformService extends AbstractAdPlatformService {

    private static final Logger log = LoggerFactory.getLogger(GooglePlatformService.class);

    private static final String SCHEME = "https";
    private static final String HOST = "googleads.googleapis.com";
    private static final String API_VERSION = "/v20/";
    private static final String GOOGLE_CONNECTION = "GOOGLE_API";

    // Default body buffer in Spring WebClient is 256 KB which Google Ads insights
    // responses (yearly daily-segmented data) blow past. Bump to 16 MB so large
    // GAQL responses don't fail with DataBufferLimitException.
    private static final WebClient webClient = WebClient.builder()
            .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
            .build();

    private final AbstractConnectionService connectionService;

    @Value("${ai.adzump.googleAds.developerToken}")
    private String googleDeveloperToken;

    public GooglePlatformService(AbstractConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @Override
    public CampaignPlatform getPlatform() {
        return CampaignPlatform.GOOGLE;
    }

    @Override
    public String getConnectionName() {
        return GOOGLE_CONNECTION;
    }

    private Mono<String> resolveDeveloperToken(String appCode, String clientCode) {
        if (this.googleDeveloperToken == null || this.googleDeveloperToken.isBlank()) {
            return Mono.error(new IllegalStateException(
                    "ai.adzump.googleAds.developerToken is not configured"));
        }
        return Mono.just(this.googleDeveloperToken);
    }

    @Override
    public Flux<CampaignMetric> fetchCampaignMetrics(
            Campaign campaign, String accessToken, String dateFrom, String dateTo) {

        String customerId = campaign.getPlatformAccountId();
        String loginCustomerId = campaign.getPlatformLoginId();
        String campaignId = campaign.getCampaignId();

        String gaql = "SELECT "
                + "campaign.id, "
                + "ad_group.id, "
                + "ad_group_ad.ad.id, "
                + "metrics.impressions, "
                + "metrics.clicks, "
                + "metrics.cost_micros, "
                + "metrics.conversions, "
                + "metrics.all_conversions, "
                + "segments.date "
                + "FROM ad_group_ad "
                + "WHERE campaign.id = " + campaignId
                + " AND segments.date BETWEEN '" + dateFrom + "' AND '" + dateTo + "'";

        return resolveDeveloperToken(campaign.getAppCode(), campaign.getClientCode())
                .flatMapMany(devToken -> search(loginCustomerId, customerId, gaql, accessToken, devToken)
                        .flatMapMany(root -> {
                            JsonNode results = root.path("results");
                            if (!results.isArray() || results.isEmpty()) {
                                return Flux.empty();
                            }
                            return Flux.fromIterable(() -> results.elements())
                                    .map(row -> mapToCampaignMetric(row, campaign));
                        }));
    }

    private CampaignMetric mapToCampaignMetric(JsonNode row, Campaign campaign) {
        JsonNode metrics = row.path("metrics");
        JsonNode segments = row.path("segments");

        long impressions = metrics.path("impressions").asLong(0);
        long clicks = metrics.path("clicks").asLong(0);
        long costMicros = metrics.path("costMicros").asLong(0);
        BigDecimal spend = BigDecimal.valueOf(costMicros, 6);
        double conversions = metrics.path("conversions").asDouble(0);
        double allConversions = metrics.path("allConversions").asDouble(0);
        String dateStr = segments.path("date").asText();
        LocalDate metricDate = LocalDate.parse(dateStr);

        return new CampaignMetric()
                .setCampaignId(campaign.getId())
                .setAppCode(campaign.getAppCode())
                .setClientCode(campaign.getClientCode())
                .setMetricDate(metricDate)
                .setImpressions(impressions)
                .setClicks(clicks)
                .setSpend(spend)
                .setPlatformWL((long) conversions)
                .setPlatformFL((long) allConversions)
                .setPlatform(CampaignPlatform.GOOGLE);
    }

    @Override
    public Mono<CampaignDetails> buildCampaignDetails(
            Campaign campaign, String adId, String accessToken) {
        return resolveDeveloperToken(campaign.getAppCode(), campaign.getClientCode())
                .flatMap(devToken -> GoogleEntityUtil.buildCampaignDetails(
                        campaign.getPlatformLoginId(),
                        campaign.getPlatformAccountId(),
                        null,
                        adId,
                        accessToken,
                        devToken));
    }

    @Override
    public Flux<DiscoveredCampaign> fetchCampaigns(
            String appCode,
            String clientCode,
            String platformAccountId,
            String platformLoginId,
            String accessToken) {

        String gaql = "SELECT campaign.id, campaign.name, campaign.advertising_channel_type, campaign.status "
                + "FROM campaign WHERE campaign.status != 'REMOVED'";

        return resolveDeveloperToken(appCode, clientCode)
                .flatMapMany(devToken -> search(platformLoginId, platformAccountId, gaql, accessToken, devToken)
                        .flatMapMany(root -> {
                            JsonNode results = root.path("results");
                            if (!results.isArray() || results.isEmpty()) return Flux.empty();
                            return Flux.fromIterable(() -> results.elements()).map(row -> {
                                JsonNode c = row.path("campaign");
                                return new DiscoveredCampaign()
                                        .setCampaignId(c.path("id").asText())
                                        .setCampaignName(c.path("name").asText())
                                        .setCampaignType(c.path("advertisingChannelType").asText(null))
                                        .setStatus(c.path("status").asText(null));
                            });
                        }));
    }

    @Override
    public Flux<DiscoveredAdset> fetchAdsets(
            String appCode,
            String clientCode,
            String platformAccountId,
            String platformLoginId,
            String externalCampaignId,
            String accessToken) {

        String gaql = "SELECT ad_group.id, ad_group.name, ad_group.status, campaign.id "
                + "FROM ad_group "
                + "WHERE campaign.id = " + externalCampaignId
                + " AND ad_group.status != 'REMOVED'";

        return resolveDeveloperToken(appCode, clientCode)
                .flatMapMany(devToken -> search(platformLoginId, platformAccountId, gaql, accessToken, devToken)
                        .flatMapMany(root -> {
                            JsonNode results = root.path("results");
                            if (!results.isArray() || results.isEmpty()) return Flux.empty();
                            return Flux.fromIterable(() -> results.elements()).map(row -> {
                                JsonNode ag = row.path("adGroup");
                                return new DiscoveredAdset()
                                        .setAdsetId(ag.path("id").asText())
                                        .setAdsetName(ag.path("name").asText())
                                        .setCampaignId(row.path("campaign").path("id").asText())
                                        .setStatus(ag.path("status").asText(null));
                            });
                        }));
    }

    @Override
    public Flux<DiscoveredAd> fetchAds(
            String appCode,
            String clientCode,
            String platformAccountId,
            String platformLoginId,
            String externalCampaignId,
            String externalAdsetId,
            String accessToken) {

        String gaql = "SELECT ad_group_ad.ad.id, ad_group_ad.ad.name, ad_group_ad.status, "
                + "ad_group.id, campaign.id "
                + "FROM ad_group_ad "
                + "WHERE ad_group.id = " + externalAdsetId
                + " AND ad_group_ad.status != 'REMOVED'";

        return resolveDeveloperToken(appCode, clientCode)
                .flatMapMany(devToken -> search(platformLoginId, platformAccountId, gaql, accessToken, devToken)
                        .flatMapMany(root -> {
                            JsonNode results = root.path("results");
                            if (!results.isArray() || results.isEmpty()) return Flux.empty();
                            return Flux.fromIterable(() -> results.elements()).map(row -> {
                                JsonNode ad = row.path("adGroupAd").path("ad");
                                return new DiscoveredAd()
                                        .setAdId(ad.path("id").asText())
                                        .setAdName(ad.path("name").asText(null))
                                        .setAdsetId(row.path("adGroup").path("id").asText())
                                        .setCampaignId(row.path("campaign").path("id").asText())
                                        .setStatus(row.path("adGroupAd").path("status").asText(null));
                            });
                        }));
    }

    private Mono<JsonNode> search(
            String loginCustomerId,
            String customerId,
            String gaql,
            String accessToken,
            String developerToken) {

        return webClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .scheme(SCHEME)
                        .host(HOST)
                        .path(API_VERSION + "customers/" + customerId + "/googleAds:search")
                        .build())
                .headers(h -> {
                    h.setBearerAuth(accessToken);
                    h.add("developer-token", developerToken);
                    if (loginCustomerId != null && !loginCustomerId.isBlank()) {
                        h.add("login-customer-id", loginCustomerId);
                    }
                })
                .bodyValue(Map.of("query", gaql))
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    /**
     * For Google, the metrics API call <em>requires</em> {@code customer_id} in
     * the URL path ({@code customers/{customerId}/googleAds:search}), so a
     * NULL {@code platformAccountId} on a Campaign row makes the sync fail
     * with HTTP 400 {@code customers/null/}. This override discovers the
     * owning customer (and its parent MCC) by walking the OAuth token's
     * accessible MCC tree:
     *
     * <ol>
     *   <li>{@code GET customers:listAccessibleCustomers} → list of MCC ids</li>
     *   <li>For each MCC: query {@code customer_client} to enumerate child accounts</li>
     *   <li>For each child: {@code SELECT campaign.id WHERE campaign.id = {campaignId}}</li>
     *   <li>First hit wins → set {@code platformAccountId} (child) and
     *       {@code platformLoginId} (MCC) on the Campaign row</li>
     * </ol>
     *
     * <p>Worst-case cost ~ N+M API calls where N = number of MCCs (typically 1-2)
     * and M = number of child accounts (typically &lt; 30). Only fires when the
     * field is missing; subsequent syncs read the populated row directly.
     */
    @Override
    public Mono<Campaign> ensurePlatformContext(Campaign campaign, String accessToken) {
        if (campaign.getPlatformAccountId() != null && !campaign.getPlatformAccountId().isBlank()) {
            return Mono.just(campaign);
        }
        if (campaign.getCampaignId() == null || campaign.getCampaignId().isBlank()) {
            return Mono.just(campaign);
        }
        String devToken = this.googleDeveloperToken;
        if (devToken == null || devToken.isBlank()) {
            log.warn("Google ensurePlatformContext: developerToken not configured, cannot derive customer_id");
            return Mono.just(campaign);
        }
        String externalCampaignId = campaign.getCampaignId();
        return listAccessibleCustomers(accessToken, devToken)
                .flatMapMany(Flux::fromIterable)
                .concatMap(mccId -> findOwnerUnderMcc(mccId, externalCampaignId, accessToken, devToken))
                .next()
                .map(owner -> {
                    log.info("Google ensurePlatformContext: campaign {} → customer={} mcc={}",
                            externalCampaignId, owner.customerId(), owner.mccId());
                    campaign.setPlatformAccountId(owner.customerId());
                    campaign.setPlatformLoginId(owner.mccId());
                    return campaign;
                })
                .defaultIfEmpty(campaign)
                .onErrorResume(e -> {
                    log.warn("Google ensurePlatformContext failed for campaign {}: {}",
                            externalCampaignId, e.toString());
                    return Mono.just(campaign);
                });
    }

    private Mono<java.util.List<String>> listAccessibleCustomers(String accessToken, String devToken) {
        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .scheme(SCHEME).host(HOST)
                        .path(API_VERSION + "customers:listAccessibleCustomers")
                        .build())
                .headers(h -> {
                    h.setBearerAuth(accessToken);
                    h.add("developer-token", devToken);
                })
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(body -> {
                    java.util.List<String> ids = new java.util.ArrayList<>();
                    JsonNode names = body.path("resourceNames");
                    if (names.isArray()) {
                        for (JsonNode n : names) {
                            String s = n.asText();
                            int slash = s.lastIndexOf('/');
                            if (slash >= 0 && slash < s.length() - 1) ids.add(s.substring(slash + 1));
                        }
                    }
                    return ids;
                });
    }

    private Flux<CampaignOwner> findOwnerUnderMcc(
            String mccId, String externalCampaignId, String accessToken, String devToken) {
        String childQuery = "SELECT customer_client.id FROM customer_client";
        return search(mccId, mccId, childQuery, accessToken, devToken)
                .flatMapMany(root -> {
                    JsonNode results = root.path("results");
                    if (!results.isArray()) return Flux.empty();
                    return Flux.fromIterable(() -> results.elements())
                            .map(r -> r.path("customerClient").path("id").asText())
                            .filter(id -> id != null && !id.isBlank());
                })
                .concatMap(childId -> {
                    String matchQuery = "SELECT campaign.id FROM campaign WHERE campaign.id = " + externalCampaignId;
                    return search(mccId, childId, matchQuery, accessToken, devToken)
                            .flatMapMany(root -> {
                                JsonNode results = root.path("results");
                                if (results.isArray() && results.size() > 0) {
                                    return Flux.just(new CampaignOwner(childId, mccId));
                                }
                                return Flux.empty();
                            })
                            .onErrorResume(e -> Flux.empty());
                });
    }

    /** Discovered ownership of a Google Ads campaign — {@code customerId} hosts it, {@code mccId} is its login parent. */
    private record CampaignOwner(String customerId, String mccId) {}
}
