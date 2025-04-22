package com.fincity.saas.entity.processor.service.base;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.flow.dto.AbstractFlowDTO;
import com.fincity.saas.commons.jooq.flow.service.AbstractFlowUpdatableService;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.entity.processor.dao.base.BaseDAO;
import com.fincity.saas.entity.processor.dto.base.BaseDto;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import java.util.HashMap;
import java.util.Map;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public abstract class BaseService<R extends UpdatableRecord<R>, D extends BaseDto<D>, O extends BaseDAO<R, D>>
        extends AbstractFlowUpdatableService<R, ULong, D, O> {

    protected ProcessorMessageResourceService msgService;
    protected CacheService cacheService;
    protected IFeignSecurityService securityService;

    protected abstract String getCacheName();

    protected String getCacheKey(String... entityNames) {
        return String.join(":", entityNames);
    }

    @Autowired
    public void setMessageResourceService(ProcessorMessageResourceService msgService) {
        this.msgService = msgService;
    }

    @Autowired
    public void setCacheService(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Autowired
    public void setSecurityService(IFeignSecurityService securityService) {
        this.securityService = securityService;
    }

    @Override
    protected Mono<ULong> getLoggedInUserId() {
        return FlatMapUtil.flatMapMono(
                        SecurityContextUtil::getUsersContextAuthentication,
                        ca -> Mono.justOrEmpty(
                                ca.isAuthenticated()
                                        ? ULong.valueOf(ca.getUser().getId())
                                        : null))
                .switchIfEmpty(msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        ProcessorMessageResourceService.INVALID_USER_FOR_CLIENT));
    }

    @Override
    protected Mono<D> updatableEntity(D entity) {

        return FlatMapUtil.flatMapMono(() -> this.read(entity.getId()), e -> {
            e.setName(entity.getName());
            e.setDescription(entity.getDescription());
            e.setTempActive(entity.isTempActive());
            e.setActive(entity.isActive());
            return Mono.just(e);
        });
    }

    @Override
    protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {

        if (fields == null || key == null) return Mono.just(new HashMap<>());

        fields.remove("createdAt");
        fields.remove("createdBy");
        fields.remove(AbstractFlowDTO.Fields.appCode);
        fields.remove(AbstractFlowDTO.Fields.clientCode);
        fields.remove(BaseDto.Fields.addedByUserId);
        fields.remove(BaseDto.Fields.code);

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
