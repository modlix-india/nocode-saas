package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorAdsets.ENTITY_PROCESSOR_ADSETS;

import com.fincity.saas.entity.processor.dao.base.BaseUpdatableDAO;
import com.fincity.saas.entity.processor.dto.Adset;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorAdsetsRecord;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
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

    /**
     * Maps each adset's external platform id ({@code ADSET_ID}) to its internal
     * primary key ({@code ID}) for one campaign. No {@link ProcessorAccess}
     * needed — used by the worker-driven metrics sync (no JWT context) to turn
     * the platform ad/adset ids on fetched metrics into our FK ids.
     */
    public Mono<java.util.Map<String, ULong>> externalToInternalIdMap(
            String appCode, String clientCode, ULong campaignId) {

        return Flux.from(this.dslContext
                        .select(ENTITY_PROCESSOR_ADSETS.ADSET_ID, ENTITY_PROCESSOR_ADSETS.ID)
                        .from(ENTITY_PROCESSOR_ADSETS)
                        .where(ENTITY_PROCESSOR_ADSETS
                                .APP_CODE
                                .eq(appCode)
                                .and(ENTITY_PROCESSOR_ADSETS.CLIENT_CODE.eq(clientCode))
                                .and(ENTITY_PROCESSOR_ADSETS.CAMPAIGN_ID.eq(campaignId))
                                .and(ENTITY_PROCESSOR_ADSETS.ADSET_ID.isNotNull())))
                .collectMap(
                        r -> r.get(ENTITY_PROCESSOR_ADSETS.ADSET_ID),
                        r -> r.get(ENTITY_PROCESSOR_ADSETS.ID));
    }

    public Mono<List<IdAndValue<ULong, String>>> listIdAndName(ProcessorAccess access, List<ULong> campaignIds) {

        var condition = ENTITY_PROCESSOR_ADSETS
                .APP_CODE
                .eq(access.getAppCode())
                .and(ENTITY_PROCESSOR_ADSETS.CLIENT_CODE.eq(access.getClientCode()));

        if (campaignIds != null && !campaignIds.isEmpty())
            condition = condition.and(ENTITY_PROCESSOR_ADSETS.CAMPAIGN_ID.in(campaignIds));

        return Flux.from(this.dslContext
                        .select(ENTITY_PROCESSOR_ADSETS.ID, ENTITY_PROCESSOR_ADSETS.ADSET_NAME)
                        .from(ENTITY_PROCESSOR_ADSETS)
                        .where(condition))
                .map(r -> IdAndValue.of(r.get(ENTITY_PROCESSOR_ADSETS.ID), r.get(ENTITY_PROCESSOR_ADSETS.ADSET_NAME)))
                .collectList();
    }
}
