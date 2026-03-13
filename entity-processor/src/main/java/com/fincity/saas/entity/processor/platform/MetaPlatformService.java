package com.fincity.saas.entity.processor.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincity.saas.entity.processor.dto.CampaignDetails;
import com.fincity.saas.entity.processor.dto.EntityIntegration;
import com.fincity.saas.entity.processor.dto.EntityResponse;
import com.fincity.saas.entity.processor.util.MetaEntityUtil;
import com.fincity.saas.entity.processor.dto.Campaign;
import com.fincity.saas.entity.processor.dto.CampaignMetric;
import com.fincity.saas.entity.processor.enums.CampaignPlatform;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class MetaPlatformService extends AbstractAdPlatformService {

    private static final String META_VERSION = "/v22.0/";
    private static final String INSIGHTS_FIELDS = "impressions,clicks,spend,actions";

    @Override
    public CampaignPlatform getPlatform() {
        return CampaignPlatform.FACEBOOK;
    }

    @Override
    public String getConnectionName() {
        return "META_API";
    }

    @Override
    public Flux<CampaignMetric> fetchCampaignMetrics(
            Campaign campaign, String accessToken, String dateFrom, String dateTo) {

        String path = META_VERSION + campaign.getCampaignId() + "/insights";
        String timeRange = "{\"since\":\"" + dateFrom + "\",\"until\":\"" + dateTo + "\"}";

        Map<String, String> queryParams = Map.of(
                "access_token", accessToken,
                "fields", INSIGHTS_FIELDS,
                "time_range", timeRange,
                "level", "ad",
                "time_increment", "1");

        return MetaEntityUtil.fetchMetaGraphData(path, queryParams)
                .flatMapMany(response -> {
                    JsonNode data = response.path("data");
                    if (!data.isArray()) {
                        return Flux.empty();
                    }
                    return Flux.fromIterable(() -> data.elements())
                            .map(row -> mapToCampaignMetric(row, campaign));
                });
    }

    private CampaignMetric mapToCampaignMetric(JsonNode row, Campaign campaign) {
        long impressions = row.path("impressions").asLong(0);
        long clicks = row.path("clicks").asLong(0);
        BigDecimal spend = new BigDecimal(row.path("spend").asText("0"));
        String dateStr = row.path("date_start").asText();
        LocalDate metricDate = LocalDate.parse(dateStr);

        long platformWL = 0;
        long platformFL = 0;
        JsonNode actions = row.path("actions");
        if (actions.isArray()) {
            for (JsonNode action : actions) {
                String actionType = action.path("action_type").asText();
                long value = action.path("value").asLong(0);
                if ("lead".equals(actionType)) {
                    platformWL += value;
                } else if ("offsite_conversion.fb_pixel_lead".equals(actionType)) {
                    platformFL += value;
                }
            }
        }

        return new CampaignMetric()
                .setCampaignId(campaign.getId())
                .setAppCode(campaign.getAppCode())
                .setClientCode(campaign.getClientCode())
                .setMetricDate(metricDate)
                .setImpressions(impressions)
                .setClicks(clicks)
                .setSpend(spend)
                .setPlatformWL(platformWL)
                .setPlatformFL(platformFL)
                .setPlatform(CampaignPlatform.FACEBOOK);
    }

    @Override
    public Mono<CampaignDetails> buildCampaignDetails(
            Campaign campaign, String adId, String accessToken) {
        return MetaEntityUtil.buildCampaignDetails(adId, accessToken);
    }

    @Override
    public Mono<String> verifyWebhook(Map<String, String> params, String verifyToken) {
        String mode = params.get("hub.mode");
        String token = params.get("hub.verify_token");
        String challenge = params.get("hub.challenge");
        return MetaEntityUtil.verifyMetaWebhook(mode, verifyToken, challenge, token)
                .map(response -> response.getBody());
    }

    @Override
    public Mono<EntityResponse> processWebhookPayload(
            JsonNode payload, EntityIntegration integration, String accessToken) {
        return MetaEntityUtil.extractMetaPayload(payload)
                .flatMap(extractedList -> {
                    if (extractedList.isEmpty()) {
                        return Mono.empty();
                    }
                    // Process the first extracted payload entry
                    MetaEntityUtil.ExtractPayload extracted = extractedList.get(0);
                    return MetaEntityUtil.fetchMetaData(
                                    extracted.leadGenId(), extracted.formId(), accessToken, null, null)
                            .flatMap(tuple -> MetaEntityUtil.normalizeMetaEntity(
                                    tuple.getT1(),
                                    tuple.getT2(),
                                    extracted.adId(),
                                    accessToken,
                                    integration,
                                    null,
                                    null,
                                    null));
                });
    }
}
