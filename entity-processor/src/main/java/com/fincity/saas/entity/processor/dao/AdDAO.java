package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorAds.ENTITY_PROCESSOR_ADS;

import com.fincity.saas.entity.processor.dao.base.BaseUpdatableDAO;
import com.fincity.saas.entity.processor.dto.Ad;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorAdsRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class AdDAO extends BaseUpdatableDAO<EntityProcessorAdsRecord, Ad> {

    protected AdDAO() {
        super(Ad.class, ENTITY_PROCESSOR_ADS, ENTITY_PROCESSOR_ADS.ID);
    }

    public Mono<Ad> readByAdId(ProcessorAccess access, String adId) {

        return Mono.from(this.dslContext
                        .selectFrom(this.table)
                        .where(ENTITY_PROCESSOR_ADS
                                .AD_ID
                                .eq(adId)
                                .and(ENTITY_PROCESSOR_ADS.APP_CODE.eq(access.getAppCode()))
                                .and(ENTITY_PROCESSOR_ADS.CLIENT_CODE.eq(access.getClientCode()))))
                .map(e -> e.into(this.pojoClass));
    }
}
