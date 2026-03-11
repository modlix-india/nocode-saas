package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorAds.ENTITY_PROCESSOR_ADS;

import com.fincity.saas.entity.processor.dao.base.BaseUpdatableDAO;
import com.fincity.saas.entity.processor.dto.Ad;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorAdsRecord;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
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

    public Mono<List<IdAndValue<ULong, String>>> listIdAndName(
            ProcessorAccess access, List<ULong> campaignIds, List<ULong> adsetIds) {

        var condition = ENTITY_PROCESSOR_ADS.APP_CODE.eq(access.getAppCode())
                .and(ENTITY_PROCESSOR_ADS.CLIENT_CODE.eq(access.getClientCode()));

        if (campaignIds != null && !campaignIds.isEmpty())
            condition = condition.and(ENTITY_PROCESSOR_ADS.CAMPAIGN_ID.in(campaignIds));

        if (adsetIds != null && !adsetIds.isEmpty())
            condition = condition.and(ENTITY_PROCESSOR_ADS.ADSET_ID.in(adsetIds));

        return Flux.from(this.dslContext
                        .select(ENTITY_PROCESSOR_ADS.ID, ENTITY_PROCESSOR_ADS.AD_NAME)
                        .from(ENTITY_PROCESSOR_ADS)
                        .where(condition))
                .map(r -> IdAndValue.of(r.get(ENTITY_PROCESSOR_ADS.ID), r.get(ENTITY_PROCESSOR_ADS.AD_NAME)))
                .collectList();
    }
}
