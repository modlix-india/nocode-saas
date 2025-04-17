package com.fincity.saas.entity.processor.service;

import java.util.HashMap;
import java.util.Map;

import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.flow.service.AbstractFlowUpdatableService;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.entity.processor.dao.BaseProcessorDAO;
import com.fincity.saas.entity.processor.dto.BaseProcessorDto;
import com.fincity.saas.entity.processor.dto.BaseProcessorDto.Fields;

import reactor.core.publisher.Mono;

@Service
public abstract class BaseProcessorService<
                R extends UpdatableRecord<R>, D extends BaseProcessorDto<D>, O extends BaseProcessorDAO<R, D>>
        extends AbstractFlowUpdatableService<R, ULong, D, O> {

    protected ProcessorMessageResourceService messageResourceService;
    protected CacheService cacheService;

    protected abstract String getCacheName();

    protected String getCacheKey(String... entityNames) {
        return String.join(":", entityNames);
    }

    @Autowired
    public void setMessageResourceService(ProcessorMessageResourceService messageResourceService) {
        this.messageResourceService = messageResourceService;
    }

    @Autowired
    public void setCacheService(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Override
    protected Mono<D> updatableEntity(D entity) {

        return FlatMapUtil.flatMapMono(() -> this.read(entity.getId()), e -> {
            if (e.getVersion() != entity.getVersion())
                return this.messageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                        AbstractMongoMessageResourceService.VERSION_MISMATCH);

            e.setName(entity.getName());
            e.setDescription(entity.getDescription());
            e.setCurrentUserId(entity.getCurrentUserId());
            e.setStatus(entity.getStatus());
            e.setSubStatus(entity.getSubStatus());
            e.setTempActive(entity.isTempActive());
            e.setActive(entity.isActive());

            e.setVersion(e.getVersion() + 1);

            return Mono.just(e);
        });
    }

    @Override
    protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {

        if (fields == null || key == null) return Mono.just(new HashMap<>());

        fields.remove("createdAt");
        fields.remove("createdBy");
        fields.remove(Fields.addedByUserId);
        fields.remove(Fields.code);

        return Mono.just(fields);
    }

    public Mono<D> getByCode(String code) {
        return this.cacheService.cacheValueOrGet(this.getCacheName(), () -> this.dao.getByCode(code), code);
    }

    public Mono<D> updateByCode(String code, D entity) {

        return FlatMapUtil.flatMapMono(
                () -> this.getByCode(code),
                e -> {
                    if (entity.getId() == null) entity.setId(e.getId());
                    return updatableEntity(entity);
                },
                (e, updatableEntity) -> this.getLoggedInUserId()
                        .map(lEntity -> {
                            updatableEntity.setUpdatedBy(lEntity);
                            return updatableEntity;
                        })
                        .defaultIfEmpty(updatableEntity),
                (e, updatableEntity, uEntity) -> this.dao.update(uEntity),
                (e, updatableEntity, uEntity, updated) -> this.evictCode(code).map(evicted -> updated));
    }

    public Mono<Integer> deleteByCode(String code) {
        return FlatMapUtil.flatMapMono(() -> this.dao.deleteByCode(code), deleted -> this.evictCode(code)
                .map(evicted -> deleted));
    }

    public Mono<Boolean> evictCode(String code) {
        return this.cacheService.evict(this.getCacheName(), code);
    }
}
