package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorCampaigns.ENTITY_PROCESSOR_CAMPAIGNS;

import com.fincity.saas.entity.processor.dao.base.BaseUpdatableDAO;
import com.fincity.saas.entity.processor.dto.Campaign;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorCampaignsRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class CampaignDAO extends BaseUpdatableDAO<EntityProcessorCampaignsRecord, Campaign> {

    protected CampaignDAO() {
        super(Campaign.class, ENTITY_PROCESSOR_CAMPAIGNS, ENTITY_PROCESSOR_CAMPAIGNS.ID);
    }

    public Mono<Campaign> readByCampaignId(ProcessorAccess access, String campaignId) {

        return Mono.from(this.dslContext
                        .selectFrom(this.table)
                        .where(ENTITY_PROCESSOR_CAMPAIGNS
                                .CAMPAIGN_ID
                                .eq(campaignId)
                                .and(ENTITY_PROCESSOR_CAMPAIGNS.APP_CODE.eq(access.getAppCode()))
                                .and(ENTITY_PROCESSOR_CAMPAIGNS.CLIENT_CODE.eq(access.getClientCode()))))
                .map(e -> e.into(this.pojoClass));
    }

    /**
     * Cross-tenant scan: every active campaign with a known platform. Used by the
     * worker-driven sync jobs (no caller security context — relies on the worker's
     * SYSTEM tenant).
     */
    public Flux<Campaign> findAllActive() {

        return Flux.from(this.dslContext
                        .selectFrom(this.table)
                        .where(ENTITY_PROCESSOR_CAMPAIGNS
                                .IS_ACTIVE
                                .isTrue()
                                .and(ENTITY_PROCESSOR_CAMPAIGNS.CAMPAIGN_PLATFORM.isNotNull())))
                .map(e -> e.into(this.pojoClass));
    }
}
