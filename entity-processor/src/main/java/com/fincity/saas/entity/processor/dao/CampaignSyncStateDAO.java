package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorCampaignSyncState.ENTITY_PROCESSOR_CAMPAIGN_SYNC_STATE;

import com.fincity.saas.entity.processor.dto.CampaignSyncState;
import com.fincity.saas.entity.processor.enums.CampaignPlatform;
import com.fincity.saas.entity.processor.jooq.enums.EntityProcessorCampaignSyncStateSyncStatus;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorCampaignSyncState;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class CampaignSyncStateDAO {

    private static final EntityProcessorCampaignSyncState SYNC = ENTITY_PROCESSOR_CAMPAIGN_SYNC_STATE;

    private static final Field<String> PLATFORM_STR = DSL.field(
            DSL.name(SYNC.getName(), "PLATFORM"), String.class);

    private final DSLContext dslContext;

    public CampaignSyncStateDAO(DSLContext dslContext) {
        this.dslContext = dslContext;
    }

    public Mono<CampaignSyncState> findByCampaignAndPlatform(
            String appCode, String clientCode, ULong campaignId, CampaignPlatform platform) {

        Condition condition = SYNC.APP_CODE.eq(appCode)
                .and(SYNC.CLIENT_CODE.eq(clientCode))
                .and(SYNC.CAMPAIGN_ID.eq(campaignId))
                .and(PLATFORM_STR.eq(platform.getLiteral()));

        return Mono.from(dslContext.selectFrom(SYNC).where(condition).limit(1))
                .map(this::mapToSyncState);
    }

    public Mono<CampaignSyncState> create(CampaignSyncState state) {

        return Mono.from(dslContext.insertInto(SYNC)
                        .set(SYNC.APP_CODE, state.getAppCode())
                        .set(SYNC.CLIENT_CODE, state.getClientCode())
                        .set(SYNC.CAMPAIGN_ID, state.getCampaignId())
                        .set(PLATFORM_STR, state.getPlatform().getLiteral())
                        .set(SYNC.SYNC_START_DATE, state.getSyncStartDate())
                        .set(SYNC.SYNC_STATUS, EntityProcessorCampaignSyncStateSyncStatus.IDLE)
                        .returning(SYNC.ID))
                .flatMap(r -> findById(r.get(SYNC.ID)));
    }

    public Mono<CampaignSyncState> markInProgress(ULong id) {

        return Mono.from(dslContext.update(SYNC)
                        .set(SYNC.SYNC_STATUS, EntityProcessorCampaignSyncStateSyncStatus.IN_PROGRESS)
                        .where(SYNC.ID.eq(id)))
                .flatMap(updated -> findById(id));
    }

    public Mono<CampaignSyncState> markComplete(ULong id, LocalDate lastSyncedTo) {

        return Mono.from(dslContext.update(SYNC)
                        .set(SYNC.SYNC_STATUS, EntityProcessorCampaignSyncStateSyncStatus.IDLE)
                        .set(SYNC.LAST_SYNC_AT, LocalDateTime.now())
                        .set(SYNC.LAST_SYNCED_TO, lastSyncedTo)
                        .setNull(SYNC.ERROR_MESSAGE)
                        .where(SYNC.ID.eq(id)))
                .flatMap(updated -> findById(id));
    }

    public Mono<CampaignSyncState> markFailed(ULong id, String errorMessage) {

        return Mono.from(dslContext.update(SYNC)
                        .set(SYNC.SYNC_STATUS, EntityProcessorCampaignSyncStateSyncStatus.FAILED)
                        .set(SYNC.ERROR_MESSAGE, errorMessage)
                        .where(SYNC.ID.eq(id)))
                .flatMap(updated -> findById(id));
    }

    private Mono<CampaignSyncState> findById(ULong id) {
        return Mono.from(dslContext.selectFrom(SYNC).where(SYNC.ID.eq(id)))
                .map(this::mapToSyncState);
    }

    private CampaignSyncState mapToSyncState(org.jooq.Record r) {
        CampaignSyncState s = new CampaignSyncState();
        s.setId(r.get(SYNC.ID));
        s.setAppCode(r.get(SYNC.APP_CODE));
        s.setClientCode(r.get(SYNC.CLIENT_CODE));
        s.setCampaignId(r.get(SYNC.CAMPAIGN_ID));
        String platformStr = r.get(PLATFORM_STR);
        if (platformStr != null) {
            s.setPlatform(CampaignPlatform.lookupLiteral(platformStr));
        }
        s.setLastSyncAt(r.get(SYNC.LAST_SYNC_AT));
        s.setLastSyncedTo(r.get(SYNC.LAST_SYNCED_TO));
        s.setSyncStartDate(r.get(SYNC.SYNC_START_DATE));
        EntityProcessorCampaignSyncStateSyncStatus syncStatus = r.get(SYNC.SYNC_STATUS);
        if (syncStatus != null) {
            s.setSyncStatus(syncStatus.getLiteral());
        }
        s.setErrorMessage(r.get(SYNC.ERROR_MESSAGE));
        s.setCreatedAt(r.get(SYNC.CREATED_AT));
        s.setUpdatedAt(r.get(SYNC.UPDATED_AT));
        return s;
    }
}
