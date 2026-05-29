package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dto.Adset;
import com.fincity.saas.entity.processor.dto.Campaign;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.discovery.DiscoveredAdset;
import com.fincity.saas.entity.processor.model.discovery.DiscoveredCampaign;
import com.fincity.saas.entity.processor.model.request.DiscoverCampaignsRequest;
import com.fincity.saas.entity.processor.model.request.EnableCampaignRequest;
import com.fincity.saas.entity.processor.platform.AbstractAdPlatformService;
import com.fincity.saas.entity.processor.platform.AdPlatformRegistry;
import com.fincity.saas.entity.processor.service.commons.AbstractConnectionService;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Orchestrates campaign discovery and enablement against external ad platforms
 * (Google, Meta). Read-only listing for the admin picker; on enable, upserts a
 * local Campaign row and mirrors its adsets + ads.
 *
 * <p>Disable is a soft-disable on the Campaign row only — adsets/ads and metric
 * history are preserved.
 */
@Service
public class CampaignDiscoveryService {

    private final AdPlatformRegistry platformRegistry;
    private final AbstractConnectionService connectionService;
    private final CampaignService campaignService;
    private final AdsetService adsetService;
    private final AdService adService;
    private final ProcessorMessageResourceService msgService;

    public CampaignDiscoveryService(
            AdPlatformRegistry platformRegistry,
            AbstractConnectionService connectionService,
            CampaignService campaignService,
            AdsetService adsetService,
            AdService adService,
            ProcessorMessageResourceService msgService) {
        this.platformRegistry = platformRegistry;
        this.connectionService = connectionService;
        this.campaignService = campaignService;
        this.adsetService = adsetService;
        this.adService = adService;
        this.msgService = msgService;
    }

    /** Lists campaigns from the platform, annotating each with whether it's already enabled locally. */
    public Mono<List<DiscoveredCampaign>> listAvailable(DiscoverCampaignsRequest request) {

        if (request.getCampaignPlatform() == null
                || request.getPlatformAccountId() == null
                || request.getPlatformAccountId().isBlank()) {
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.MISSING_PARAMETERS,
                    "campaignPlatform, platformAccountId");
        }

        AbstractAdPlatformService platform = this.platformRegistry.getService(request.getCampaignPlatform());

        return FlatMapUtil.flatMapMono(
                        this.campaignService::hasAccess,
                        access -> this.connectionService.getMarketingPlatformOAuth2Token(
                                access.getClientCode(), platform.getConnectionName()),
                        (access, token) -> platform.fetchCampaigns(
                                        access.getAppCode(),
                                        access.getClientCode(),
                                        request.getPlatformAccountId(),
                                        request.getPlatformLoginId(),
                                        token)
                                .collectList(),
                        (access, token, discovered) -> this.annotateEnabled(access, discovered))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CampaignDiscoveryService.listAvailable"));
    }

    /** For each discovered campaign, sets {@code enabled=true} if a local active Campaign row exists. */
    private Mono<List<DiscoveredCampaign>> annotateEnabled(
            ProcessorAccess access, List<DiscoveredCampaign> discovered) {

        return Flux.fromIterable(discovered)
                .concatMap(d -> this.campaignService
                        .readByCampaignId(access, d.getCampaignId())
                        .map(local -> d.setEnabled(Boolean.TRUE.equals(local.isActive())))
                        .defaultIfEmpty(d.setEnabled(false)))
                .collectList();
    }

    /**
     * Enables a campaign: upserts the local Campaign row and mirrors its adsets
     * and ads from the platform. Returns the persisted Campaign with its local FK.
     */
    public Mono<Campaign> enable(EnableCampaignRequest request) {

        if (request.getCampaignPlatform() == null
                || request.getExternalCampaignId() == null
                || request.getExternalCampaignId().isBlank()
                || request.getPlatformAccountId() == null
                || request.getPlatformAccountId().isBlank()) {
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.MISSING_PARAMETERS,
                    "campaignPlatform, externalCampaignId, platformAccountId");
        }

        List<Identity> productIdentities = effectiveProductIdentities(request);
        AbstractAdPlatformService platform = this.platformRegistry.getService(request.getCampaignPlatform());

        return FlatMapUtil.flatMapMono(
                        this.campaignService::hasAccess,
                        access -> this.upsertCampaign(access, request),
                        (access, campaign) -> productIdentities.isEmpty()
                                ? Mono.just(campaign)
                                : this.campaignService
                                        .setProducts(campaign.getId(), productIdentities)
                                        .thenReturn(campaign),
                        (access, campaign, linkedCampaign) -> this.connectionService
                                .getMarketingPlatformOAuth2Token(
                                        access.getClientCode(), platform.getConnectionName())
                                .flatMap(token ->
                                        this.mirrorAdsetsAndAds(access, platform, linkedCampaign, request, token))
                                .thenReturn(linkedCampaign))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CampaignDiscoveryService.enable"));
    }

    /**
     * Resolves the products to link on enable: the many-to-many {@code productIds}
     * when present, else the deprecated single {@code productId}, else none (the
     * campaign is enabled with no product link — valid for auto-discovery).
     */
    private static List<Identity> effectiveProductIdentities(EnableCampaignRequest request) {
        if (request.getProductIds() != null && !request.getProductIds().isEmpty()) return request.getProductIds();
        if (request.getProductId() != null && request.getProductId().getULongId() != null)
            return List.of(request.getProductId());
        return List.of();
    }

    private Mono<Campaign> upsertCampaign(ProcessorAccess access, EnableCampaignRequest request) {

        return this.campaignService
                .readByCampaignId(access, request.getExternalCampaignId())
                .flatMap(existing -> {
                    existing.setCampaignName(request.getExternalCampaignName());
                    existing.setCampaignType(request.getCampaignType());
                    existing.setCampaignPlatform(request.getCampaignPlatform());
                    existing.setPlatformAccountId(request.getPlatformAccountId());
                    existing.setPlatformLoginId(request.getPlatformLoginId());
                    existing.setActive(Boolean.TRUE);
                    return this.campaignService.update(existing);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    Campaign fresh = new Campaign()
                            .setCampaignId(request.getExternalCampaignId())
                            .setCampaignName(request.getExternalCampaignName())
                            .setCampaignType(request.getCampaignType())
                            .setCampaignPlatform(request.getCampaignPlatform())
                            .setPlatformAccountId(request.getPlatformAccountId())
                            .setPlatformLoginId(request.getPlatformLoginId());
                    fresh.setActive(Boolean.TRUE);
                    return this.campaignService.create(fresh);
                }));
    }

    private Mono<Void> mirrorAdsetsAndAds(
            ProcessorAccess access,
            AbstractAdPlatformService platform,
            Campaign campaign,
            EnableCampaignRequest request,
            String token) {

        return platform.fetchAdsets(
                        access.getAppCode(),
                        access.getClientCode(),
                        request.getPlatformAccountId(),
                        request.getPlatformLoginId(),
                        request.getExternalCampaignId(),
                        token)
                .concatMap(discoveredAdset -> this.mirrorOneAdset(access, platform, campaign, request, token, discoveredAdset))
                .then();
    }

    private Mono<Adset> mirrorOneAdset(
            ProcessorAccess access,
            AbstractAdPlatformService platform,
            Campaign campaign,
            EnableCampaignRequest request,
            String token,
            DiscoveredAdset discoveredAdset) {

        return this.adsetService
                .readOrCreate(access, discoveredAdset.getAdsetId(), discoveredAdset.getAdsetName(), campaign.getId())
                .flatMap(adset -> platform.fetchAds(
                                access.getAppCode(),
                                access.getClientCode(),
                                request.getPlatformAccountId(),
                                request.getPlatformLoginId(),
                                request.getExternalCampaignId(),
                                discoveredAdset.getAdsetId(),
                                token)
                        .concatMap(discoveredAd -> this.adService.readOrCreate(
                                access,
                                discoveredAd.getAdId(),
                                discoveredAd.getAdName(),
                                discoveredAd.getThumbnailUrl(),
                                discoveredAd.getCreativeType(),
                                adset.getId(),
                                campaign.getId()))
                        .then()
                        .thenReturn(adset));
    }

    /**
     * Cross-tenant: walks every active campaign across all clients and
     * re-mirrors its adsets + ads from the platform. Failures on individual
     * campaigns are swallowed so one bad tenant doesn't block the rest.
     */
    public Mono<java.util.Map<String, Object>> refreshAllActive() {
        java.util.concurrent.atomic.AtomicInteger touched = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger errors = new java.util.concurrent.atomic.AtomicInteger();

        return this.campaignService
                .findAllActive()
                .filter(c -> this.platformRegistry.isSupported(c.getCampaignPlatform()))
                .flatMap(
                        campaign -> this.refreshOneFromCampaign(campaign)
                                .doOnSuccess(v -> touched.incrementAndGet())
                                .onErrorResume(e -> {
                                    errors.incrementAndGet();
                                    return Mono.empty();
                                }),
                        3)
                .then(Mono.fromSupplier(() -> java.util.Map.<String, Object>of(
                        "campaignsTouched", touched.get(),
                        "errors", errors.get())))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CampaignDiscoveryService.refreshAllActive"));
    }

    private Mono<Void> refreshOneFromCampaign(Campaign campaign) {
        AbstractAdPlatformService platform = this.platformRegistry.getService(campaign.getCampaignPlatform());
        ProcessorAccess access = ProcessorAccess.of(campaign.getAppCode(), campaign.getClientCode(), true, null, null);

        return this.connectionService
                .getMarketingPlatformOAuth2Token(campaign.getClientCode(), platform.getConnectionName())
                .flatMap(token -> platform.fetchAdsets(
                                campaign.getAppCode(),
                                campaign.getClientCode(),
                                campaign.getPlatformAccountId(),
                                campaign.getPlatformLoginId(),
                                campaign.getCampaignId(),
                                token)
                        .concatMap(discoveredAdset -> this.adsetService
                                .readOrCreate(
                                        access,
                                        discoveredAdset.getAdsetId(),
                                        discoveredAdset.getAdsetName(),
                                        campaign.getId())
                                .flatMap(adset -> platform.fetchAds(
                                                campaign.getAppCode(),
                                                campaign.getClientCode(),
                                                campaign.getPlatformAccountId(),
                                                campaign.getPlatformLoginId(),
                                                campaign.getCampaignId(),
                                                discoveredAdset.getAdsetId(),
                                                token)
                                        .concatMap(discoveredAd -> this.adService.readOrCreate(
                                                access,
                                                discoveredAd.getAdId(),
                                                discoveredAd.getAdName(),
                                                discoveredAd.getThumbnailUrl(),
                                                discoveredAd.getCreativeType(),
                                                adset.getId(),
                                                campaign.getId()))
                                        .then()))
                        .then());
    }

    /** Soft-disables a local campaign. Adsets, ads and metric history are preserved. */
    public Mono<Campaign> disable(ULong localCampaignId) {

        return FlatMapUtil.flatMapMono(
                        this.campaignService::hasAccess,
                        access -> this.campaignService.read(localCampaignId),
                        (access, campaign) -> {
                            campaign.setActive(Boolean.FALSE);
                            return this.campaignService.update(campaign);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CampaignDiscoveryService.disable"));
    }

    /**
     * Lists available pixels/datasets on the given Meta ad account so the operator
     * can pick the one to bind to a campaign for CAPI dispatch. Returns the raw
     * pixels {@code data} array from Meta — UI maps {id, name, is_unavailable}.
     */
    public Mono<com.fasterxml.jackson.databind.JsonNode> listMetaPixels(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.MISSING_PARAMETERS,
                    "accountId");
        }
        com.fincity.saas.entity.processor.platform.MetaPlatformService meta =
                (com.fincity.saas.entity.processor.platform.MetaPlatformService)
                        this.platformRegistry.getService(
                                com.fincity.saas.entity.processor.enums.CampaignPlatform.FACEBOOK);
        return FlatMapUtil.flatMapMono(
                        this.campaignService::hasAccess,
                        access -> this.connectionService.getMarketingPlatformOAuth2Token(
                                access.getClientCode(), meta.getConnectionName()),
                        (access, token) -> meta.listPixels(accountId, token))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CampaignDiscoveryService.listMetaPixels"));
    }

    /**
     * Lists every Meta ad account the connected OAuth token can read. Backs the
     * operator-facing ad-account picker in {@code campaignConfig}. The UI uses
     * this to populate an account dropdown so a campaign whose own ad account has
     * no pixel can be re-bound to a sibling account that does — pixels are
     * Business-Manager assets and can be shared across accounts.
     */
    public Mono<com.fasterxml.jackson.databind.JsonNode> listMetaAccounts() {
        com.fincity.saas.entity.processor.platform.MetaPlatformService meta =
                (com.fincity.saas.entity.processor.platform.MetaPlatformService)
                        this.platformRegistry.getService(
                                com.fincity.saas.entity.processor.enums.CampaignPlatform.FACEBOOK);
        return FlatMapUtil.flatMapMono(
                        this.campaignService::hasAccess,
                        access -> this.connectionService.getMarketingPlatformOAuth2Token(
                                access.getClientCode(), meta.getConnectionName()),
                        (access, token) -> meta.listAccounts(token))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CampaignDiscoveryService.listMetaAccounts"));
    }

    /**
     * Creates a new pixel/dataset on the given Meta ad account. Returns the new
     * pixel JSON ({id, name}) which the UI uses to populate the picker selection.
     */
    public Mono<com.fasterxml.jackson.databind.JsonNode> createMetaPixel(String accountId, String name) {
        if (accountId == null || accountId.isBlank() || name == null || name.isBlank()) {
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.MISSING_PARAMETERS,
                    "accountId, name");
        }
        com.fincity.saas.entity.processor.platform.MetaPlatformService meta =
                (com.fincity.saas.entity.processor.platform.MetaPlatformService)
                        this.platformRegistry.getService(
                                com.fincity.saas.entity.processor.enums.CampaignPlatform.FACEBOOK);
        return FlatMapUtil.flatMapMono(
                        this.campaignService::hasAccess,
                        access -> this.connectionService.getMarketingPlatformOAuth2Token(
                                access.getClientCode(), meta.getConnectionName()),
                        (access, token) -> meta.createPixel(accountId, name, token))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CampaignDiscoveryService.createMetaPixel"));
    }
}
