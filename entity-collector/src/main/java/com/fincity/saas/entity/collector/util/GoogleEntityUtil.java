package com.fincity.saas.entity.collector.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincity.saas.entity.collector.dto.CampaignDetails;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public final class GoogleEntityUtil {

    private static final WebClient webClient = WebClient.create();

    private static final String SCHEME = "https";
    private static final String HOST = "googleads.googleapis.com";
    private static final String DEVELOPER_TOKEN = "7U26WAwto0ESzLeoNJ6Zgw";
    // Bump the version to the one your account uses (v17 at the time of writing)
    private static final String API_VERSION = "/v20/";

    /**
     * Fetches Campaign, AdGroup and Ad details for a Google Ads Ad ID using GAQL search.
     *
     * @param customerId Google Ads customer ID (without dashes)
     * @param adId       the Ad ID to lookup (numeric string)
     * @param accessToken    OAuth2 access token (Bearer)
     */
    public static Mono<CampaignDetails> buildCampaignDetails(
            String loginCustomerId,
            String customerId,
            String adId,
            String accessToken) {

        if (accessToken == null || accessToken.isBlank()) {
            return Mono.empty();
        }

        String gaql = buildGaqlForAdId(adId);
        return search(loginCustomerId, customerId, gaql, accessToken)
                .flatMap(root -> {
                    JsonNode results = root.path("results");
                    if (!results.isArray() || results.size() == 0) {
                        return Mono.empty();
                    }
                    JsonNode row = results.get(0);

                    JsonNode adNode = row.path("adGroupAd").path("ad");
                    JsonNode adGroupNode = row.path("adGroup");
                    JsonNode campaignNode = row.path("campaign");

                    CampaignDetails cd = new CampaignDetails();
                    cd.setAdId(adNode.path("id").asText());
                    cd.setAdName(adNode.path("name").asText());
                    cd.setAdSetId(adGroupNode.path("id").asText());    // AdGroup -> mapped to AdSet fields
                    cd.setAdSetName(adGroupNode.path("name").asText());
                    cd.setCampaignId(campaignNode.path("id").asText());
                    cd.setCampaignName(campaignNode.path("name").asText());

                    return Mono.just(cd);
                });
    }

    private static String buildGaqlForAdId(String adId) {
        // adId is numeric; GAQL expects unquoted number in WHERE
        return "SELECT\n" +
                "  ad_group_ad.ad.id,\n" +
                "  ad_group_ad.ad.name,\n" +
                "  ad_group.id,\n" +
                "  ad_group.name,\n" +
                "  campaign.id,\n" +
                "  campaign.name\n" +
                "FROM ad_group_ad\n" +
                "WHERE ad_group_ad.ad.id = " + adId;
    }

    private static Mono<JsonNode> search(
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
                .bodyValue(java.util.Map.of("query", gaql))
                .retrieve()
                .bodyToMono(com.fasterxml.jackson.databind.JsonNode.class);
    }
}