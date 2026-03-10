package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorAdsets.ENTITY_PROCESSOR_ADSETS;

import com.fincity.saas.entity.processor.dao.base.BaseUpdatableDAO;
import com.fincity.saas.entity.processor.dto.Adset;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorAdsetsRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class AdsetDAO extends BaseUpdatableDAO<EntityProcessorAdsetsRecord, Adset> {

    protected AdsetDAO() {
        super(Adset.class, ENTITY_PROCESSOR_ADSETS, ENTITY_PROCESSOR_ADSETS.ID);
    }

    public Mono<Adset> readByAdsetId(ProcessorAccess access, String adsetId) {

        return Mono.from(this.dslContext
                        .selectFrom(this.table)
                        .where(ENTITY_PROCESSOR_ADSETS
                                .ADSET_ID
                                .eq(adsetId)
                                .and(ENTITY_PROCESSOR_ADSETS.APP_CODE.eq(access.getAppCode()))
                                .and(ENTITY_PROCESSOR_ADSETS.CLIENT_CODE.eq(access.getClientCode()))))
                .map(e -> e.into(this.pojoClass));
    }
}
