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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class AdService extends BaseUpdatableService<EntityProcessorAdsRecord, Ad, AdDAO> {

    private static final Logger log = LoggerFactory.getLogger(AdService.class);

    @Override
    protected boolean canOutsideCreate() {
        return false;
    }


    public Mono<Ad> readOrCreate(
            ProcessorAccess access,
            String adId,
            String adName,
            String thumbnailUrl,
            String creativeType,
            ULong adsetDbId,
            ULong campaignDbId) {

        if (adId == null) return Mono.empty();

        return this.dao.readByAdId(access, adId)
                .flatMap(existing -> {
                    // Refresh discovery fields on already-discovered ads: thumbnail (captured
                    // after the row was first created) and name (e.g. Google RSAs that had no
                    // name until the type-based fallback was added).
                    boolean thumbChanged = thumbnailUrl != null
                            && !thumbnailUrl.isBlank()
                            && !thumbnailUrl.equals(existing.getThumbnailUrl());
                    boolean nameChanged =
                            adName != null && !adName.isBlank() && !adName.equals(existing.getAdName());
                    if (thumbChanged || nameChanged) {
                        String newName = nameChanged ? adName : existing.getAdName();
                        String newThumb = thumbChanged ? thumbnailUrl : existing.getThumbnailUrl();
                        String newType = thumbChanged ? creativeType : existing.getCreativeType();
                        return this.dao.updateDiscoveredFields(existing.getId(), newName, newThumb, newType)
                                .thenReturn(existing.setAdName(newName)
                                        .setThumbnailUrl(newThumb)
                                        .setCreativeType(newType))
                                .onErrorResume(e -> {
                                    log.warn("Failed to refresh discovery fields for ad id={}: {}",
                                            existing.getId(), e.toString());
                                    return Mono.just(existing);
                                });
                    }
                    return Mono.just(existing);
                })
                .switchIfEmpty(super.createInternal(access, new Ad()
                                .setAdId(adId)
                                .setAdName(adName)
                                .setThumbnailUrl(thumbnailUrl)
                                .setCreativeType(creativeType)
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
