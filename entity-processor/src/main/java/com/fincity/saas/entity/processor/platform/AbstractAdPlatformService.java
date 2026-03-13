package com.fincity.saas.entity.processor.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincity.saas.entity.processor.dto.CampaignDetails;
import com.fincity.saas.entity.processor.dto.EntityIntegration;
import com.fincity.saas.entity.processor.dto.EntityResponse;
import com.fincity.saas.entity.processor.dto.Campaign;
import com.fincity.saas.entity.processor.dto.CampaignMetric;
import com.fincity.saas.entity.processor.enums.CampaignPlatform;
import java.util.Map;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class AbstractAdPlatformService {

    public abstract CampaignPlatform getPlatform();

    public abstract Flux<CampaignMetric> fetchCampaignMetrics(
            Campaign campaign, String accessToken, String dateFrom, String dateTo);

    public abstract Mono<CampaignDetails> buildCampaignDetails(
            Campaign campaign, String adId, String accessToken);

    public Mono<String> verifyWebhook(Map<String, String> params, String verifyToken) {
        return Mono.empty();
    }

    public Mono<EntityResponse> processWebhookPayload(
            JsonNode payload, EntityIntegration integration, String accessToken) {
        return Mono.empty();
    }

    public abstract String getConnectionName();
}
