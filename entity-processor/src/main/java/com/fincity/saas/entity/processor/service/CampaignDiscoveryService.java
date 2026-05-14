package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dto.Adset;
import com.fincity.saas.entity.processor.dto.Campaign;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.discovery.DiscoveredAdset;
import com.fincity.saas.entity.processor.model.discovery.DiscoveredCampaign;
import com.fincity.saas.entity.processor.model.request.DiscoverCampaignsRequest;
import com.fincity.saas.entity.processor.model.request.EnableCampaignRequest;
import com.fincity.saas.entity.processor.platform.AbstractAdPlatformService;
import com.fincity.saas.entity.processor.platform.AdPlatformRegistry;
import com.fincity.saas.entity.processor.service.commons.AbstractConnectionService;
import com.fincity.saas.entity.processor.service.product.ProductService;
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
    private final ProductService productService;
    private final ProcessorMessageResourceService msgService;

    public CampaignDiscoveryService(
            AdPlatformRegistry platformRegistry,
            AbstractConnectionService connectionService,
            CampaignService campaignService,
            AdsetService adsetService,
            AdService adService,
            ProductService productService,
            ProcessorMessageResourceService msgService) {
        this.platformRegistry = platformRegistry;
        this.connectionService = connectionService;
        this.campaignService = campaignService;
        this.adsetService = adsetService;
        this.adService = adService;
        this.productService = productService;
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
                        access -> this.connectionService.getConnectionOAuth2Token(
                                access.getAppCode(), access.getClientCode(), platform.getConnectionName()),
                        (access, token) -> platform.fetchCampaigns(
                                        request.getPlatformAccountId(), request.getPlatformLoginId(), token)
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
                || request.getProductId() == null
                || request.getProductId().getULongId() == null
                || request.getPlatformAccountId() == null
                || request.getPlatformAccountId().isBlank()) {
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.MISSING_PARAMETERS,
                    "productId, campaignPlatform, externalCampaignId, platformAccountId");
        }

        AbstractAdPlatformService platform = this.platformRegistry.getService(request.getCampaignPlatform());

        return FlatMapUtil.flatMapMono(
                        this.campaignService::hasAccess,
                        access -> this.productService.readByIdentity(access, request.getProductId()),
                        (access, product) -> {
                            if (!product.isActive())
                                return this.msgService.<Campaign>throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        ProcessorMessageResourceService.PRODUCT_NOT_ACTIVE);
                            return this.upsertCampaign(access, request, product.getId());
                        },
                        (access, product, campaign) -> this.connectionService
                                .getConnectionOAuth2Token(
                                        access.getAppCode(), access.getClientCode(), platform.getConnectionName())
                                .flatMap(token -> this.mirrorAdsetsAndAds(access, platform, campaign, request, token))
                                .thenReturn(campaign))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CampaignDiscoveryService.enable"));
    }

    private Mono<Campaign> upsertCampaign(ProcessorAccess access, EnableCampaignRequest request, ULong productId) {

        return this.campaignService
                .readByCampaignId(access, request.getExternalCampaignId())
                .flatMap(existing -> {
                    existing.setProductId(productId);
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
                            .setPlatformLoginId(request.getPlatformLoginId())
                            .setProductId(productId);
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
                                request.getPlatformAccountId(),
                                request.getPlatformLoginId(),
                                request.getExternalCampaignId(),
                                discoveredAdset.getAdsetId(),
                                token)
                        .concatMap(discoveredAd -> this.adService.readOrCreate(
                                access,
                                discoveredAd.getAdId(),
                                discoveredAd.getAdName(),
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
                .getConnectionOAuth2Token(campaign.getAppCode(), campaign.getClientCode(), platform.getConnectionName())
                .flatMap(token -> platform.fetchAdsets(
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
                                                campaign.getPlatformAccountId(),
                                                campaign.getPlatformLoginId(),
                                                campaign.getCampaignId(),
                                                discoveredAdset.getAdsetId(),
                                                token)
                                        .concatMap(discoveredAd -> this.adService.readOrCreate(
                                                access,
                                                discoveredAd.getAdId(),
                                                discoveredAd.getAdName(),
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
}
