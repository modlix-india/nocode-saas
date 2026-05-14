package com.fincity.saas.entity.processor.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincity.saas.entity.processor.dto.CampaignDetails;
import com.fincity.saas.entity.processor.dto.EntityIntegration;
import com.fincity.saas.entity.processor.dto.EntityResponse;
import com.fincity.saas.entity.processor.dto.Campaign;
import com.fincity.saas.entity.processor.dto.CampaignMetric;
import com.fincity.saas.entity.processor.enums.CampaignPlatform;
import com.fincity.saas.entity.processor.model.discovery.DiscoveredAd;
import com.fincity.saas.entity.processor.model.discovery.DiscoveredAdset;
import com.fincity.saas.entity.processor.model.discovery.DiscoveredCampaign;
import java.util.Map;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class AbstractAdPlatformService {

    public abstract CampaignPlatform getPlatform();

    public abstract Flux<CampaignMetric> fetchCampaignMetrics(
            Campaign campaign, String accessToken, String dateFrom, String dateTo);

    public abstract Mono<CampaignDetails> buildCampaignDetails(
            Campaign campaign, String adId, String accessToken);

    /**
     * Lists all campaigns under the given platform account for the admin
     * "select campaigns to enable" picker. Read-only against the external API;
     * does not write to local DB.
     */
    public abstract Flux<DiscoveredCampaign> fetchCampaigns(
            String platformAccountId, String platformLoginId, String accessToken);

    /**
     * Lists all adsets under the given external campaign. Used when an admin
     * enables a campaign, so we mirror adsets locally.
     */
    public abstract Flux<DiscoveredAdset> fetchAdsets(
            String platformAccountId,
            String platformLoginId,
            String externalCampaignId,
            String accessToken);

    /**
     * Lists all ads under the given external adset. Used during the same enable
     * flow to mirror ads.
     */
    public abstract Flux<DiscoveredAd> fetchAds(
            String platformAccountId,
            String platformLoginId,
            String externalCampaignId,
            String externalAdsetId,
            String accessToken);

    public Mono<String> verifyWebhook(Map<String, String> params, String verifyToken) {
        return Mono.empty();
    }

    public Mono<EntityResponse> processWebhookPayload(
            JsonNode payload, EntityIntegration integration, String accessToken) {
        return Mono.empty();
    }

    public abstract String getConnectionName();
}
