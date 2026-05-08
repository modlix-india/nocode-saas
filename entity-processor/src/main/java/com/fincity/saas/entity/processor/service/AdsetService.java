package com.fincity.saas.entity.processor.service;

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

    @Override
    protected boolean canOutsideCreate() {
        return false;
    }


    public Mono<Adset> readOrCreate(ProcessorAccess access, String adsetId, String adsetName, ULong campaignDbId) {

        return this.dao.readByAdsetId(access, adsetId)
                .switchIfEmpty(super.createInternal(access, new Adset()
                                .setAdsetId(adsetId)
                                .setAdsetName(adsetName)
                                .setCampaignId(campaignDbId))
                        .onErrorResume(DataAccessException.class,
                                e -> this.dao.readByAdsetId(access, adsetId)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AdsetService.readOrCreate"));
    }

    public Mono<List<IdAndValue<ULong, String>>> readByCampaignIds(List<ULong> campaignIds) {
        return this.hasAccess()
                .flatMap(access -> this.dao.listIdAndName(access, campaignIds));
    }
}
