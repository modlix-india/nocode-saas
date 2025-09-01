package com.fincity.saas.entity.processor.service;

import org.springframework.stereotype.Service;

import com.fincity.saas.entity.processor.dao.PartnerDAO;
import com.fincity.saas.entity.processor.dto.Partner;
import com.fincity.saas.entity.processor.enums.IEntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorPartnersRecord;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;

@Service
public class PartnerService extends BaseUpdatableService<EntityProcessorPartnersRecord, Partner, PartnerDAO>
        implements IEntitySeries {

    private static final String PARTNER_CACHE = "Partner";

    @Override
    protected String getCacheName() {
        return PARTNER_CACHE;
    }

    @Override
    protected boolean canOutsideCreate() {
        return Boolean.FALSE;
    }

}
