package com.fincity.saas.entity.processor.service;

import com.fincity.saas.entity.processor.dao.CampaignSyncStateDAO;
import com.fincity.saas.entity.processor.dto.Campaign;
import com.fincity.saas.entity.processor.dto.CampaignSyncState;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class CampaignSyncStateService {

    private static final int RE_SYNC_DAYS = 3;

    private final CampaignSyncStateDAO campaignSyncStateDAO;

    public CampaignSyncStateService(CampaignSyncStateDAO campaignSyncStateDAO) {
        this.campaignSyncStateDAO = campaignSyncStateDAO;
    }

    /**
     * Get or create a sync state for a campaign.
     * If no sync state exists, creates one with syncStartDate = campaign creation date.
     */
    public Mono<CampaignSyncState> getOrCreate(Campaign campaign) {
        return campaignSyncStateDAO
                .findByCampaignAndPlatform(
                        campaign.getAppCode(),
                        campaign.getClientCode(),
                        campaign.getId(),
                        campaign.getCampaignPlatform())
                .switchIfEmpty(Mono.defer(() -> {
                    CampaignSyncState newState = new CampaignSyncState()
                            .setAppCode(campaign.getAppCode())
                            .setClientCode(campaign.getClientCode())
                            .setCampaignId(campaign.getId())
                            .setPlatform(campaign.getCampaignPlatform())
                            .setSyncStartDate(campaign.getCreatedAt() != null
                                    ? campaign.getCreatedAt().toLocalDate()
                                    : LocalDate.now().minusMonths(3))
                            .setSyncStatus("IDLE");
                    return campaignSyncStateDAO.create(newState);
                }));
    }

    /**
     * Calculate the date from which to start syncing.
     * If never synced before: use syncStartDate.
     * If synced before: use lastSyncedTo minus RE_SYNC_DAYS (overlap for retroactive attribution).
     */
    public LocalDate calculateSyncFrom(CampaignSyncState syncState) {
        if (syncState.getLastSyncedTo() == null) {
            return syncState.getSyncStartDate();
        }
        return syncState.getLastSyncedTo().minusDays(RE_SYNC_DAYS);
    }

    public Mono<CampaignSyncState> markInProgress(CampaignSyncState syncState) {
        return campaignSyncStateDAO.markInProgress(syncState.getId());
    }

    public Mono<CampaignSyncState> markComplete(CampaignSyncState syncState, String dateTo) {
        return campaignSyncStateDAO.markComplete(syncState.getId(), LocalDate.parse(dateTo));
    }

    public Mono<CampaignSyncState> markFailed(CampaignSyncState syncState, String errorMessage) {
        return campaignSyncStateDAO.markFailed(syncState.getId(), errorMessage);
    }
}
