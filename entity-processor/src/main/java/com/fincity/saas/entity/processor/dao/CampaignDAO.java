package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorCampaigns.ENTITY_PROCESSOR_CAMPAIGNS;

import com.fincity.saas.entity.processor.dao.base.BaseUpdatableDAO;
import com.fincity.saas.entity.processor.dto.Campaign;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorCampaignsRecord;
import org.springframework.stereotype.Component;

@Component
public class CampaignDAO extends BaseUpdatableDAO<EntityProcessorCampaignsRecord, Campaign> {

    protected CampaignDAO() {
        super(Campaign.class, ENTITY_PROCESSOR_CAMPAIGNS, ENTITY_PROCESSOR_CAMPAIGNS.ID);
    }
}
