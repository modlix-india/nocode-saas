package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.AdsetDAO;
import com.fincity.saas.entity.processor.dto.Adset;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorAdsetsRecord;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class AdsetService extends BaseUpdatableService<EntityProcessorAdsetsRecord, Adset, AdsetDAO> {

    private static final String ADSET_CACHE = "adset";

    @Override
    protected String getCacheName() {
        return ADSET_CACHE;
    }

    @Override
    protected boolean canOutsideCreate() {
        return false;
    }

    @Override
    protected Mono<Boolean> evictCache(Adset entity) {
        return Mono.zip(
                super.evictCache(entity),
                super.cacheService.evict(
                        this.getCacheName(),
                        super.getCacheKey(entity.getAppCode(), entity.getClientCode(), entity.getAdsetId())),
                (baseEvicted, adsetEvicted) -> baseEvicted && adsetEvicted);
    }

    public Mono<Adset> readOrCreate(ProcessorAccess access, String adsetId, String adsetName, ULong campaignDbId) {

        return super.cacheService
                .cacheValueOrGet(
                        this.getCacheName(),
                        () -> FlatMapUtil.flatMapMono(

                                () -> this.dao.readByAdsetId(access, adsetId)
                                        .switchIfEmpty(super.createInternal(access, new Adset()
                                                        .setAdsetId(adsetId)
                                                        .setAdsetName(adsetName)
                                                        .setCampaignId(campaignDbId))
                                                .onErrorResume(DataAccessException.class,
                                                        e -> this.dao.readByAdsetId(access, adsetId))),

                                adset -> Mono.just(adset)),

                        super.getCacheKey(access.getAppCode(), access.getClientCode(), adsetId))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AdsetService.readOrCreate"));
    }

    public Mono<List<IdAndValue<ULong, String>>> readByCampaignIds(List<ULong> campaignIds) {
        return this.hasAccess()
                .flatMap(access -> this.dao.listIdAndName(access, campaignIds));
    }
}
