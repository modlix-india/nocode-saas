package com.fincity.saas.entity.processor.service;

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

    @Override
    protected boolean canOutsideCreate() {
        return false;
    }


    public Mono<Ad> readOrCreate(ProcessorAccess access, String adId, String adName, ULong adsetDbId, ULong campaignDbId) {

        if (adId == null) return Mono.empty();

        return this.dao.readByAdId(access, adId)
                .switchIfEmpty(super.createInternal(access, new Ad()
                                .setAdId(adId)
                                .setAdName(adName)
                                .setAdsetId(adsetDbId)
                                .setCampaignId(campaignDbId))
                        .onErrorResume(DataAccessException.class,
                                e -> this.dao.readByAdId(access, adId)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AdService.readOrCreate"));
    }

    public Mono<List<IdAndValue<ULong, String>>> listIdAndName(List<ULong> campaignIds, List<ULong> adsetIds) {
        return this.hasAccess()
                .flatMap(access -> this.dao.listIdAndName(access, campaignIds, adsetIds));
    }
}
