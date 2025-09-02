package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_PARTNERS;

import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.entity.processor.dao.base.BaseUpdatableDAO;
import com.fincity.saas.entity.processor.dto.Partner;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorPartnersRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;

import reactor.core.publisher.Mono;

@Component
public class PartnerDAO extends BaseUpdatableDAO<EntityProcessorPartnersRecord, Partner> {

    protected PartnerDAO() {
        super(Partner.class, ENTITY_PROCESSOR_PARTNERS, ENTITY_PROCESSOR_PARTNERS.ID);
    }

    public Mono<Partner> getPartnerByClientId(ProcessorAccess access, ULong clientId) {
        return
    }
}
