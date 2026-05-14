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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class GooglePlatformService extends AbstractAdPlatformService {

    private static final String SCHEME = "https";
    private static final String HOST = "googleads.googleapis.com";
    private static final String DEVELOPER_TOKEN = "7U26WAwto0ESzLeoNJ6Zgw";
    private static final String API_VERSION = "/v20/";

    private static final WebClient webClient = WebClient.create();

    @Override
    public CampaignPlatform getPlatform() {
        return CampaignPlatform.GOOGLE;
    }

    @Override
    public String getConnectionName() {
        return "GOOGLE_API";
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

        return search(loginCustomerId, customerId, gaql, accessToken)
                .flatMapMany(root -> {
                    JsonNode results = root.path("results");
                    if (!results.isArray() || results.isEmpty()) {
                        return Flux.empty();
                    }
                    return Flux.fromIterable(() -> results.elements())
                            .map(row -> mapToCampaignMetric(row, campaign));
                });
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
        return GoogleEntityUtil.buildCampaignDetails(
                campaign.getPlatformLoginId(),
                campaign.getPlatformAccountId(),
                null,
                adId,
                accessToken);
    }

    @Override
    public Flux<DiscoveredCampaign> fetchCampaigns(
            String platformAccountId, String platformLoginId, String accessToken) {

        String gaql = "SELECT campaign.id, campaign.name, campaign.advertising_channel_type, campaign.status "
                + "FROM campaign WHERE campaign.status != 'REMOVED'";

        return search(platformLoginId, platformAccountId, gaql, accessToken).flatMapMany(root -> {
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
        });
    }

    @Override
    public Flux<DiscoveredAdset> fetchAdsets(
            String platformAccountId,
            String platformLoginId,
            String externalCampaignId,
            String accessToken) {

        String gaql = "SELECT ad_group.id, ad_group.name, ad_group.status, campaign.id "
                + "FROM ad_group "
                + "WHERE campaign.id = " + externalCampaignId
                + " AND ad_group.status != 'REMOVED'";

        return search(platformLoginId, platformAccountId, gaql, accessToken).flatMapMany(root -> {
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
        });
    }

    @Override
    public Flux<DiscoveredAd> fetchAds(
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

        return search(platformLoginId, platformAccountId, gaql, accessToken).flatMapMany(root -> {
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
        });
    }

    private Mono<JsonNode> search(
            String loginCustomerId,
            String customerId,
            String gaql,
            String accessToken) {

        return webClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .scheme(SCHEME)
                        .host(HOST)
                        .path(API_VERSION + "customers/" + customerId + "/googleAds:search")
                        .build())
                .headers(h -> {
                    h.setBearerAuth(accessToken);
                    h.add("developer-token", DEVELOPER_TOKEN);
                    h.add("login-customer-id", loginCustomerId);
                })
                .bodyValue(Map.of("query", gaql))
                .retrieve()
                .bodyToMono(JsonNode.class);
    }
}
