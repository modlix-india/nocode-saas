package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.AdDAO;
import com.fincity.saas.entity.processor.dto.Ad;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorAdsRecord;
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
public class AdService extends BaseUpdatableService<EntityProcessorAdsRecord, Ad, AdDAO> {

    private static final String AD_CACHE = "ad";

    @Override
    protected String getCacheName() {
        return AD_CACHE;
    }

    @Override
    protected boolean canOutsideCreate() {
        return false;
    }

    @Override
    protected Mono<Boolean> evictCache(Ad entity) {
        return Mono.zip(
                super.evictCache(entity),
                super.cacheService.evict(
                        this.getCacheName(),
                        super.getCacheKey(entity.getAppCode(), entity.getClientCode(), entity.getAdId())),
                (baseEvicted, adEvicted) -> baseEvicted && adEvicted);
    }

    public Mono<Ad> readOrCreate(ProcessorAccess access, String adId, String adName, ULong adsetDbId, ULong campaignDbId) {

        if (adId == null) return Mono.empty();

        return super.cacheService
                .cacheValueOrGet(
                        this.getCacheName(),
                        () -> FlatMapUtil.flatMapMono(

                                () -> this.dao.readByAdId(access, adId)
                                        .switchIfEmpty(super.createInternal(access, new Ad()
                                                        .setAdId(adId)
                                                        .setAdName(adName)
                                                        .setAdsetId(adsetDbId)
                                                        .setCampaignId(campaignDbId))
                                                .onErrorResume(DataAccessException.class,
                                                        e -> this.dao.readByAdId(access, adId))),

                                ad -> Mono.just(ad)),

                        super.getCacheKey(access.getAppCode(), access.getClientCode(), adId))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AdService.readOrCreate"));
    }

    public Mono<List<IdAndValue<ULong, String>>> listIdAndName(List<ULong> campaignIds, List<ULong> adsetIds) {
        return this.hasAccess()
                .flatMap(access -> this.dao.listIdAndName(access, campaignIds, adsetIds));
    }
}
