package com.fincity.saas.entity.processor.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincity.saas.entity.processor.dto.CampaignDetails;
import com.fincity.saas.entity.processor.dto.EntityIntegration;
import com.fincity.saas.entity.processor.dto.EntityResponse;
import com.fincity.saas.entity.processor.util.MetaEntityUtil;
import com.fincity.saas.entity.processor.dto.Campaign;
import com.fincity.saas.entity.processor.dto.CampaignMetric;
import com.fincity.saas.entity.processor.enums.CampaignPlatform;
import com.fincity.saas.entity.processor.model.discovery.DiscoveredAd;
import com.fincity.saas.entity.processor.model.discovery.DiscoveredAdset;
import com.fincity.saas.entity.processor.model.discovery.DiscoveredCampaign;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class MetaPlatformService extends AbstractAdPlatformService {

    private static final Logger log = LoggerFactory.getLogger(MetaPlatformService.class);

    private static final String META_VERSION = "/v22.0/";
    private static final String P_ACCESS_TOKEN = "access_token";
    private static final String P_FIELDS = "fields";
    private static final String ADSPIXELS_EDGE = "/adspixels";
    // ad_id/adset_id/campaign_id must be requested explicitly — at level=ad the
    // insights edge returns only the fields you ask for (plus the date), so
    // without these the rows carry no breakdown id and collapse to campaign grain.
    private static final String INSIGHTS_FIELDS =
            "impressions,clicks,spend,actions,ad_id,adset_id,campaign_id";

    @Override
    public CampaignPlatform getPlatform() {
        return CampaignPlatform.FACEBOOK;
    }

    @Override
    public String getConnectionName() {
        return "META_API";
    }

    /**
     * For Meta, resolve {@code platformAccountId} (ad account id) and
     * {@code platformDatasetId} (Pixel ID) when the row is missing them.
     *
     * <ul>
     *   <li>{@code platformAccountId} comes from {@code GET /{campaignId}?fields=account_id}.</li>
     *   <li>{@code platformDatasetId} comes from {@code GET /act_{accountId}/adspixels}.
     *       If exactly one available pixel is returned, use it. If multiple, pick the first
     *       available one and log a warning so the operator can override via the
     *       client's META_API connection {@code connectionDetails.defaultPixelId} or by
     *       editing the campaign row directly.</li>
     * </ul>
     *
     * <p>Meta metrics fetch ({@code GET /{campaignId}/insights}) works fine without
     * either id, so the discovery path doesn't fail when these are unresolved; the
     * Phase 4 CAPI dispatch path is the consumer that requires {@code platformDatasetId}.
     */
    @Override
    public Mono<Campaign> ensurePlatformContext(Campaign campaign, String accessToken) {
        return ensureAccountId(campaign, accessToken).flatMap(c -> ensurePixelId(c, accessToken));
    }

    private Mono<Campaign> ensureAccountId(Campaign campaign, String accessToken) {
        if (campaign.getPlatformAccountId() != null && !campaign.getPlatformAccountId().isBlank()) {
            return Mono.just(campaign);
        }
        if (campaign.getCampaignId() == null || campaign.getCampaignId().isBlank()) {
            return Mono.just(campaign);
        }
        return MetaEntityUtil.fetchMetaGraphData(
                        META_VERSION + campaign.getCampaignId(),
                        Map.of(P_ACCESS_TOKEN, accessToken, P_FIELDS, "account_id"))
                .map(body -> {
                    String accountId = body.path("account_id").asText(null);
                    if (accountId != null && !accountId.isBlank()) {
                        log.info("Meta ensurePlatformContext: campaign {} → account_id {}",
                                campaign.getCampaignId(), accountId);
                        campaign.setPlatformAccountId(accountId);
                    } else {
                        log.warn("Meta ensurePlatformContext: campaign {} returned no account_id (body={})",
                                campaign.getCampaignId(), body);
                    }
                    return campaign;
                })
                .onErrorResume(e -> {
                    log.warn("Meta ensurePlatformContext (account_id) failed for campaign {}: {}",
                            campaign.getCampaignId(), e.toString());
                    return Mono.just(campaign);
                });
    }

    /**
     * Lists all pixels/datasets owned by the given ad account. Backs the operator-facing
     * pixel picker in {@code campaignConfig}. Returns the raw {@code data} array from
     * Meta — caller maps each entry's {id, name, is_unavailable}.
     */
    public Mono<JsonNode> listPixels(String accountId, String accessToken) {
        String actPrefixed = accountId.startsWith("act_") ? accountId : "act_" + accountId;
        return MetaEntityUtil.fetchMetaGraphData(
                        META_VERSION + actPrefixed + ADSPIXELS_EDGE,
                        Map.of(P_ACCESS_TOKEN, accessToken, P_FIELDS, "id,name,is_unavailable"))
                .map(body -> body.path("data"));
    }

    /**
     * Creates a new pixel/dataset on the given ad account. Backs the "Create new"
     * inline button on the operator's pixel picker. Returns the new pixel as
     * {id, name} JSON.
     */
    public Mono<JsonNode> createPixel(String accountId, String name, String accessToken) {
        String actPrefixed = accountId.startsWith("act_") ? accountId : "act_" + accountId;
        return MetaEntityUtil.postMetaGraphData(
                META_VERSION + actPrefixed + ADSPIXELS_EDGE,
                Map.of(P_ACCESS_TOKEN, accessToken, "name", name));
    }

    private Mono<Campaign> ensurePixelId(Campaign campaign, String accessToken) {
        if (campaign.getPlatformDatasetId() != null && !campaign.getPlatformDatasetId().isBlank()) {
            return Mono.just(campaign);
        }
        String accountId = campaign.getPlatformAccountId();
        if (accountId == null || accountId.isBlank()) {
            return Mono.just(campaign);
        }
        // Meta API returns account ids without the "act_" prefix on /{campaign}?fields=account_id,
        // but the adspixels edge requires it. Normalize here.
        String actPrefixed = accountId.startsWith("act_") ? accountId : "act_" + accountId;
        return MetaEntityUtil.fetchMetaGraphData(
                        META_VERSION + actPrefixed + ADSPIXELS_EDGE,
                        Map.of(P_ACCESS_TOKEN, accessToken, P_FIELDS, "id,name,is_unavailable"))
                .map(body -> {
                    String pixelId = pickPixel(body, campaign);
                    if (pixelId != null) {
                        log.info("Meta ensurePlatformContext: campaign {} → pixel_id {}",
                                campaign.getCampaignId(), pixelId);
                        campaign.setPlatformDatasetId(pixelId);
                    }
                    return campaign;
                })
                .onErrorResume(e -> {
                    log.warn("Meta ensurePlatformContext (pixel_id) failed for campaign {}: {}",
                            campaign.getCampaignId(), e.toString());
                    return Mono.just(campaign);
                });
    }

    /**
     * Returns the pixel id to bind to the campaign row. Picks the only available
     * pixel; if there are multiple, picks the first available and logs a warning so
     * the operator knows to override via the connection's {@code defaultPixelId}
     * or by editing the campaign row.
     */
    private static String pickPixel(JsonNode body, Campaign campaign) {
        JsonNode data = body.path("data");
        if (!data.isArray() || data.isEmpty()) {
            // Dump the raw body so the operator can tell apart "account has no pixels"
            // (data: []) from "token lacks ads_management scope" (error object) from
            // "paginated, all on next page" (paging.next). Without this we silently
            // treat all three the same.
            log.warn("Meta adspixels: account {} returned no pixels for campaign {} (raw body={})",
                    campaign.getPlatformAccountId(), campaign.getCampaignId(), body);
            return null;
        }
        String firstAvailable = null;
        int availableCount = 0;
        for (JsonNode pixel : data) {
            String id = pixel.path("is_unavailable").asBoolean(false)
                    ? null
                    : pixel.path("id").asText(null);
            if (id != null && !id.isBlank()) {
                availableCount++;
                if (firstAvailable == null) firstAvailable = id;
            }
        }
        if (availableCount > 1) {
            log.warn("Meta adspixels: account {} has {} available pixels for campaign {}; picked {}. "
                            + "If this isn't the right one, set defaultPixelId on the META_API connection "
                            + "or edit the campaign row's platform_dataset_id.",
                    campaign.getPlatformAccountId(), availableCount, campaign.getCampaignId(), firstAvailable);
        }
        return firstAvailable;
    }

    @Override
    public Flux<CampaignMetric> fetchCampaignMetrics(
            Campaign campaign, String accessToken, String dateFrom, String dateTo) {

        String path = META_VERSION + campaign.getCampaignId() + "/insights";
        String timeRange = "{\"since\":\"" + dateFrom + "\",\"until\":\"" + dateTo + "\"}";

        Map<String, String> queryParams = Map.of(
                P_ACCESS_TOKEN, accessToken,
                P_FIELDS, INSIGHTS_FIELDS,
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

        // level=ad insights include adset_id / ad_id on every row. Capture them
        // so MetricsSyncService can resolve internal ids and emit adset/ad-grain
        // rows (otherwise every per-ad row collapses to campaign grain).
        String externalAdsetId = row.path("adset_id").asText(null);
        String externalAdId = row.path("ad_id").asText(null);

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
                .setPlatform(CampaignPlatform.FACEBOOK)
                .setExternalAdsetId(externalAdsetId)
                .setExternalAdId(externalAdId);
    }

    @Override
    public Mono<CampaignDetails> buildCampaignDetails(
            Campaign campaign, String adId, String accessToken) {
        return MetaEntityUtil.buildCampaignDetails(adId, accessToken);
    }

    @Override
    public Flux<DiscoveredCampaign> fetchCampaigns(
            String appCode,
            String clientCode,
            String platformAccountId,
            String platformLoginId,
            String accessToken) {

        String account = normalizeAdAccountId(platformAccountId);
        String path = META_VERSION + account + "/campaigns";
        Map<String, String> params =
                Map.of(P_ACCESS_TOKEN, accessToken, P_FIELDS, "id,name,objective,status", "limit", "200");

        return MetaEntityUtil.fetchMetaGraphData(path, params).flatMapMany(response -> {
            JsonNode data = response.path("data");
            if (!data.isArray()) return Flux.empty();
            return Flux.fromIterable(() -> data.elements())
                    .map(node -> new DiscoveredCampaign()
                            .setCampaignId(node.path("id").asText())
                            .setCampaignName(node.path("name").asText())
                            .setCampaignType(node.path("objective").asText(null))
                            .setStatus(node.path("status").asText(null)));
        });
    }

    @Override
    public Flux<DiscoveredAdset> fetchAdsets(
            String appCode,
            String clientCode,
            String platformAccountId,
            String platformLoginId,
            String externalCampaignId,
            String accessToken) {

        String path = META_VERSION + externalCampaignId + "/adsets";
        Map<String, String> params =
                Map.of(P_ACCESS_TOKEN, accessToken, P_FIELDS, "id,name,status,campaign_id", "limit", "200");

        return MetaEntityUtil.fetchMetaGraphData(path, params).flatMapMany(response -> {
            JsonNode data = response.path("data");
            if (!data.isArray()) return Flux.empty();
            return Flux.fromIterable(() -> data.elements())
                    .map(node -> new DiscoveredAdset()
                            .setAdsetId(node.path("id").asText())
                            .setAdsetName(node.path("name").asText())
                            .setCampaignId(node.path("campaign_id").asText(externalCampaignId))
                            .setStatus(node.path("status").asText(null)));
        });
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

        String path = META_VERSION + externalAdsetId + "/ads";
        Map<String, String> params = Map.of(
                P_ACCESS_TOKEN,
                accessToken,
                P_FIELDS,
                "id,name,status,adset_id,campaign_id,creative{thumbnail_url,image_url,object_type}",
                "limit",
                "200");

        return MetaEntityUtil.fetchMetaGraphData(path, params).flatMapMany(response -> {
            JsonNode data = response.path("data");
            if (!data.isArray()) return Flux.empty();
            return Flux.fromIterable(() -> data.elements())
                    .map(node -> {
                        JsonNode creative = node.path("creative");
                        String thumb = creative.path("thumbnail_url").asText(null);
                        if (thumb == null || thumb.isBlank())
                            thumb = creative.path("image_url").asText(null);
                        return new DiscoveredAd()
                                .setAdId(node.path("id").asText())
                                .setAdName(node.path("name").asText(null))
                                .setAdsetId(node.path("adset_id").asText(externalAdsetId))
                                .setCampaignId(node.path("campaign_id").asText(externalCampaignId))
                                .setStatus(node.path("status").asText(null))
                                .setThumbnailUrl(thumb)
                                .setCreativeType(creative.path("object_type").asText(null));
                    });
        });
    }

    /** Meta ad-account IDs must be queried with the {@code act_} prefix. */
    private static String normalizeAdAccountId(String accountId) {
        if (accountId == null) return null;
        return accountId.startsWith("act_") ? accountId : "act_" + accountId;
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
