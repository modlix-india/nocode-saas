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

    /** Direct column update for discovery-refreshed fields — bypasses full-entity update/validation. */
    public Mono<Integer> updateDiscoveredFields(
            ULong id, String adName, String thumbnailUrl, String creativeType) {
        return Mono.from(this.dslContext
                .update(ENTITY_PROCESSOR_ADS)
                .set(ENTITY_PROCESSOR_ADS.AD_NAME, adName)
                .set(ENTITY_PROCESSOR_ADS.THUMBNAIL_URL, thumbnailUrl)
                .set(ENTITY_PROCESSOR_ADS.CREATIVE_TYPE, creativeType)
                .where(ENTITY_PROCESSOR_ADS.ID.eq(id)));
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

    /**
     * Maps each ad's external platform id ({@code AD_ID}) to its internal
     * primary key ({@code ID}) for one campaign. No {@link ProcessorAccess}
     * needed — used by the worker-driven metrics sync (no JWT context). See
     * {@code AdsetDAO.externalToInternalIdMap}.
     */
    public Mono<java.util.Map<String, ULong>> externalToInternalIdMap(
            String appCode, String clientCode, ULong campaignId) {

        return Flux.from(this.dslContext
                        .select(ENTITY_PROCESSOR_ADS.AD_ID, ENTITY_PROCESSOR_ADS.ID)
                        .from(ENTITY_PROCESSOR_ADS)
                        .where(ENTITY_PROCESSOR_ADS
                                .APP_CODE
                                .eq(appCode)
                                .and(ENTITY_PROCESSOR_ADS.CLIENT_CODE.eq(clientCode))
                                .and(ENTITY_PROCESSOR_ADS.CAMPAIGN_ID.eq(campaignId))
                                .and(ENTITY_PROCESSOR_ADS.AD_ID.isNotNull())))
                .collectMap(
                        r -> r.get(ENTITY_PROCESSOR_ADS.AD_ID),
                        r -> r.get(ENTITY_PROCESSOR_ADS.ID));
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
