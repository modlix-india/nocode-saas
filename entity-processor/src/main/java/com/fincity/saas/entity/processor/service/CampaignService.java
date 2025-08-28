package com.fincity.saas.entity.processor.service;

import com.fincity.saas.entity.processor.dao.CampaignDAO;
import com.fincity.saas.entity.processor.dto.Campaign;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorCampaignsRecord;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import org.springframework.stereotype.Service;

@Service
public class CampaignService extends BaseUpdatableService<EntityProcessorCampaignsRecord, Campaign, CampaignDAO> {

    private static final String CAMPAIGN_CACHE = "campaign";

    @Override
    protected String getCacheName() {
        return CAMPAIGN_CACHE;
    }

    @Override
    protected boolean canOutsideCreate() {
        return false;
    }
}
