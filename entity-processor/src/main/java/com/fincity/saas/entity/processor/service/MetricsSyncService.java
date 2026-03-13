package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.platform.AbstractAdPlatformService;
import com.fincity.saas.entity.processor.platform.AdPlatformRegistry;
import com.fincity.saas.entity.processor.service.commons.AbstractConnectionService;
import com.fincity.saas.entity.processor.dto.Campaign;
import com.fincity.saas.entity.processor.dto.CampaignSyncState;
import com.fincity.saas.entity.processor.service.CampaignMetricService;
import com.fincity.saas.entity.processor.service.CampaignService;
import com.fincity.saas.entity.processor.service.CampaignSyncStateService;
import java.time.LocalDate;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class MetricsSyncService {

    private final AdPlatformRegistry platformRegistry;
    private final AbstractConnectionService connectionService;
    private final CampaignMetricService campaignMetricService;
    private final CampaignSyncStateService syncStateService;
    private final CampaignService campaignService;

    public MetricsSyncService(
            AdPlatformRegistry platformRegistry,
            AbstractConnectionService connectionService,
            CampaignMetricService campaignMetricService,
            CampaignSyncStateService syncStateService,
            CampaignService campaignService) {
        this.platformRegistry = platformRegistry;
        this.connectionService = connectionService;
        this.campaignMetricService = campaignMetricService;
        this.syncStateService = syncStateService;
        this.campaignService = campaignService;
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

        return syncStateService.markInProgress(syncState)
                .then(connectionService.getConnectionOAuth2Token(appCode, clientCode, platform.getConnectionName()))
                .flatMap(token -> platform.fetchCampaignMetrics(campaign, token, dateFromStr, dateTo)
                        .collectList())
                .flatMap(campaignMetricService::bulkUpsert)
                .then(syncStateService.markComplete(syncState, dateTo))
                .then()
                .onErrorResume(e -> syncStateService.markFailed(syncState, e.getMessage()).then());
    }
}
