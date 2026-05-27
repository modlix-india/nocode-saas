package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.AdDAO;
import com.fincity.saas.entity.processor.dao.AdsetDAO;
import com.fincity.saas.entity.processor.dao.CampaignDAO;
import com.fincity.saas.entity.processor.platform.AbstractAdPlatformService;
import com.fincity.saas.entity.processor.platform.AdPlatformRegistry;
import com.fincity.saas.entity.processor.service.commons.AbstractConnectionService;
import com.fincity.saas.entity.processor.dto.Campaign;
import com.fincity.saas.entity.processor.dto.CampaignMetric;
import com.fincity.saas.entity.processor.dto.CampaignSyncState;
import com.fincity.saas.entity.processor.service.CampaignMetricService;
import com.fincity.saas.entity.processor.service.CampaignService;
import com.fincity.saas.entity.processor.service.CampaignSyncStateService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jooq.types.ULong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class MetricsSyncService {

    private static final Logger log = LoggerFactory.getLogger(MetricsSyncService.class);

    private final AdPlatformRegistry platformRegistry;
    private final AbstractConnectionService connectionService;
    private final CampaignMetricService campaignMetricService;
    private final CampaignSyncStateService syncStateService;
    private final CampaignService campaignService;
    private final CampaignDAO campaignDAO;
    private final AdsetDAO adsetDAO;
    private final AdDAO adDAO;

    public MetricsSyncService(
            AdPlatformRegistry platformRegistry,
            AbstractConnectionService connectionService,
            CampaignMetricService campaignMetricService,
            CampaignSyncStateService syncStateService,
            CampaignService campaignService,
            CampaignDAO campaignDAO,
            AdsetDAO adsetDAO,
            AdDAO adDAO) {
        this.platformRegistry = platformRegistry;
        this.connectionService = connectionService;
        this.campaignMetricService = campaignMetricService;
        this.syncStateService = syncStateService;
        this.campaignService = campaignService;
        this.campaignDAO = campaignDAO;
        this.adsetDAO = adsetDAO;
        this.adDAO = adDAO;
    }

    /**
     * Sync metrics for a list of campaigns. Incremental: only fetches data
     * from the last sync point (with 3-day overlap for retroactive attribution updates).
     */
    public Mono<Void> syncCampaigns(List<ULong> campaignIds, String appCode, String clientCode) {
        return Flux.fromIterable(campaignIds)
                .flatMap(campaignId -> syncSingleCampaign(campaignId, appCode, clientCode), 3)
                .then()
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MetricsSyncService.syncCampaigns"));
    }

    /**
     * Cross-tenant: walks every active campaign across all clients and syncs
     * metrics. Used by the worker-driven hourly job. Failures on individual
     * campaigns are swallowed (logged via sync_state) so one bad tenant doesn't
     * block the rest.
     */
    public Mono<java.util.Map<String, Object>> syncAllActive() {
        java.util.concurrent.atomic.AtomicInteger touched = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger errors = new java.util.concurrent.atomic.AtomicInteger();

        return campaignService
                .findAllActive()
                .flatMap(
                        campaign -> syncSingleCampaignDirect(campaign)
                                .doOnSuccess(v -> touched.incrementAndGet())
                                .onErrorResume(e -> {
                                    errors.incrementAndGet();
                                    log.warn("syncSingleCampaign failed for campaign id={} client={} app={} platform={}: {}",
                                            campaign.getId(), campaign.getClientCode(), campaign.getAppCode(),
                                            campaign.getCampaignPlatform(), e.toString(), e);
                                    return Mono.empty();
                                }),
                        3)
                .then(Mono.fromSupplier(() -> java.util.Map.<String, Object>of(
                        "campaignsTouched", touched.get(),
                        "errors", errors.get())))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MetricsSyncService.syncAllActive"));
    }

    /**
     * Worker-internal variant of {@link #syncSingleCampaign}. The caller (typically
     * {@link #syncAllActive}) already has the {@link Campaign} object loaded from
     * {@code findAllActive()}, so this method skips the {@code campaignService.read(id)}
     * call — which would invoke {@code hasAccess()} and fail under the no-JWT
     * worker-triggered path.
     */
    private Mono<Void> syncSingleCampaignDirect(Campaign campaign) {
        if (campaign.getCampaignPlatform() == null
                || !platformRegistry.isSupported(campaign.getCampaignPlatform())) {
            return Mono.empty();
        }
        return syncStateService
                .getOrCreate(campaign)
                .flatMap(syncState ->
                        executSync(campaign, syncState, campaign.getAppCode(), campaign.getClientCode()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MetricsSyncService.syncSingleCampaignDirect"));
    }

    private Mono<Void> syncSingleCampaign(ULong campaignId, String appCode, String clientCode) {
        return FlatMapUtil.flatMapMono(
                () -> campaignService.read(campaignId),
                campaign -> {
                    if (campaign.getCampaignPlatform() == null
                            || !platformRegistry.isSupported(campaign.getCampaignPlatform())) {
                        return Mono.<Void>empty();
                    }
                    return syncStateService.getOrCreate(campaign)
                            .flatMap(syncState -> executSync(campaign, syncState, appCode, clientCode));
                }
        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "MetricsSyncService.syncSingleCampaign"));
    }

    private Mono<Void> executSync(Campaign campaign, CampaignSyncState syncState,
            String appCode, String clientCode) {

        LocalDate dateFrom = syncStateService.calculateSyncFrom(syncState);
        String dateTo = LocalDate.now().toString();
        String dateFromStr = dateFrom.toString();

        AbstractAdPlatformService platform = platformRegistry.getService(campaign.getCampaignPlatform());

        // Snapshot original values BEFORE ensurePlatformContext mutates the campaign,
        // so the diff in persistResolvedIfChanged can detect newly-set fields.
        final String origAccountId = campaign.getPlatformAccountId();
        final String origLoginId = campaign.getPlatformLoginId();
        final String origDatasetId = campaign.getPlatformDatasetId();

        return syncStateService.markInProgress(syncState)
                .then(connectionService.getMarketingPlatformOAuth2Token(clientCode, platform.getConnectionName()))
                .flatMap(token -> platform
                        // Lazy backfill of platformAccountId / platformLoginId / platformDatasetId
                        // when the row came in NULL (e.g. created via the marketingai UI page
                        // whose payload omits those fields). Self-heals on first sync.
                        .ensurePlatformContext(campaign, token)
                        .flatMap(resolved -> persistResolvedIfChanged(
                                        resolved, origAccountId, origLoginId, origDatasetId)
                                .thenReturn(resolved))
                        .flatMap(resolved -> platform.fetchCampaignMetrics(resolved, token, dateFromStr, dateTo)
                                .collectList()
                                .flatMap(adRows -> expandToGrains(resolved, adRows))))
                .flatMap(campaignMetricService::bulkUpsert)
                .then(syncStateService.markComplete(syncState, dateTo))
                .then()
                .onErrorResume(e -> syncStateService.markFailed(syncState, e.getMessage()).then());
    }

    /**
     * The platform fetch returns one row per ad per day (ad-grain), each carrying
     * the platform's external adset/ad ids. The campaign report reads three
     * separate metric grains:
     * <ul>
     *   <li>ad-grain: {@code ADSET_ID} + {@code AD_ID} set</li>
     *   <li>adset-grain: {@code ADSET_ID} set, {@code AD_ID} null</li>
     *   <li>campaign-grain: both null</li>
     * </ul>
     * so we resolve the external ids to our internal FK ids and roll the ad rows
     * up into all three grains. Before this, every per-ad row collapsed onto the
     * campaign-grain unique key, leaving only the last ad's numbers — under-counting
     * campaign totals and producing zero adset/ad rows.
     *
     * <p>Campaign-grain sums EVERY fetched row (even ads not yet discovered into
     * {@code entity_processor_ads}) so campaign totals stay correct; the adset and
     * ad grains only include rows whose external id resolved to an internal id.
     */
    private Mono<List<CampaignMetric>> expandToGrains(Campaign campaign, List<CampaignMetric> adRows) {
        if (adRows == null || adRows.isEmpty()) return Mono.just(List.of());

        return Mono.zip(
                        adsetDAO.externalToInternalIdMap(
                                campaign.getAppCode(), campaign.getClientCode(), campaign.getId()),
                        adDAO.externalToInternalIdMap(
                                campaign.getAppCode(), campaign.getClientCode(), campaign.getId()))
                .map(maps -> {
                    Map<String, ULong> adsetMap = maps.getT1();
                    Map<String, ULong> adMap = maps.getT2();

                    Map<String, CampaignMetric> campaignGrain = new HashMap<>();
                    Map<String, CampaignMetric> adsetGrain = new HashMap<>();
                    Map<String, CampaignMetric> adGrain = new HashMap<>();

                    for (CampaignMetric r : adRows) {
                        ULong adsetId =
                                r.getExternalAdsetId() == null ? null : adsetMap.get(r.getExternalAdsetId());
                        ULong adId = r.getExternalAdId() == null ? null : adMap.get(r.getExternalAdId());
                        String date = String.valueOf(r.getMetricDate());

                        accumulate(campaignGrain, date, r, null, null);
                        if (adsetId != null) accumulate(adsetGrain, adsetId + "|" + date, r, adsetId, null);
                        if (adId != null) accumulate(adGrain, adId + "|" + date, r, adsetId, adId);
                    }

                    List<CampaignMetric> out = new ArrayList<>(
                            campaignGrain.size() + adsetGrain.size() + adGrain.size());
                    out.addAll(campaignGrain.values());
                    out.addAll(adsetGrain.values());
                    out.addAll(adGrain.values());
                    return out;
                });
    }

    /** Folds {@code src}'s numbers into the accumulator at {@code key}, tagging the target grain via {@code adsetId}/{@code adId}. */
    private static void accumulate(
            Map<String, CampaignMetric> acc, String key, CampaignMetric src, ULong adsetId, ULong adId) {
        CampaignMetric m = acc.computeIfAbsent(key, k -> new CampaignMetric()
                .setAppCode(src.getAppCode())
                .setClientCode(src.getClientCode())
                .setCampaignId(src.getCampaignId())
                .setAdsetId(adsetId)
                .setAdId(adId)
                .setMetricDate(src.getMetricDate())
                .setPlatform(src.getPlatform())
                .setCurrency(src.getCurrency())
                .setSpend(BigDecimal.ZERO));
        m.setImpressions(m.getImpressions() + src.getImpressions());
        m.setClicks(m.getClicks() + src.getClicks());
        m.setSpend((m.getSpend() == null ? BigDecimal.ZERO : m.getSpend())
                .add(src.getSpend() == null ? BigDecimal.ZERO : src.getSpend()));
        m.setPlatformWL(m.getPlatformWL() + src.getPlatformWL());
        m.setPlatformFL(m.getPlatformFL() + src.getPlatformFL());
    }

    /**
     * If {@code ensurePlatformContext} populated any of the three platform-id fields
     * that were previously NULL, persist them to {@code entity_processor_campaigns}.
     * No-op when nothing changed. Errors are swallowed (logged) — the metrics
     * fetch should still proceed even if the backfill UPDATE fails.
     *
     * <p>Original values are passed in as separate parameters (not by re-reading
     * from a "before" Campaign object) because {@code ensurePlatformContext}
     * mutates the campaign in place — by the time we get here, the
     * post-mutation campaign is the only object reference we have.
     */
    private Mono<Void> persistResolvedIfChanged(
            Campaign resolved, String origAccountId, String origLoginId, String origDatasetId) {
        String accountId = diff(origAccountId, resolved.getPlatformAccountId());
        String loginId = diff(origLoginId, resolved.getPlatformLoginId());
        String datasetId = diff(origDatasetId, resolved.getPlatformDatasetId());
        if (accountId == null && loginId == null && datasetId == null) {
            return Mono.empty();
        }
        return campaignDAO
                .updatePlatformIds(resolved.getId(), accountId, loginId, datasetId)
                .doOnNext(n -> log.info(
                        "Backfilled platform-context for campaign id={} (rows={}, accountId={}, loginId={}, datasetId={})",
                        resolved.getId(), n, accountId, loginId, datasetId))
                .then()
                .onErrorResume(e -> {
                    log.warn("Failed to persist resolved platform-context for campaign id={}: {}",
                            resolved.getId(), e.toString());
                    return Mono.empty();
                });
    }

    /** Returns {@code resolved} only when it's a non-blank value that differs from {@code original}. */
    private static String diff(String original, String resolved) {
        if (resolved == null || resolved.isBlank()) return null;
        if (resolved.equals(original)) return null;
        return resolved;
    }
}
