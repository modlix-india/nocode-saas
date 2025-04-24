package com.fincity.saas.entity.processor.service.base;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.flow.dto.AbstractFlowDTO;
import com.fincity.saas.commons.jooq.flow.service.AbstractFlowUpdatableService;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.entity.processor.dao.base.BaseDAO;
import com.fincity.saas.entity.processor.dto.base.BaseDto;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

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

    protected String getCacheKey(Object... entityNames) {
        return String.join(":", Stream.of(entityNames).map(Object::toString).toArray(String[]::new));
    }

    protected Mono<Boolean> evictCache(D entity) {
        return this.cacheService
                .evict(this.getCacheName(), entity.getCode())
                .flatMap(evicted -> this.cacheService.evict(getCacheName(), entity.getId()));
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

    public Mono<D> readInternal(ULong id) {
        return this.cacheService.cacheValueOrGet(this.getCacheName(), () -> this.dao.readInternal(id), id);
    }

    public Mono<D> readByCode(String code) {
        return this.cacheService.cacheValueOrGet(this.getCacheName(), () -> this.dao.readByCode(code), code);
    }

    public Mono<D> updateByCode(String code, D entity) {

        return FlatMapUtil.flatMapMono(
                () -> this.readByCode(code),
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
                (e, updatableEntity, uEntity, updated) ->
                        this.evictCache(updated).map(evicted -> updated));
    }

    public Mono<Integer> deleteByCode(String code) {
        return FlatMapUtil.flatMapMono(
                () -> this.readByCode(code),
                entity -> this.dao.deleteByCode(code),
                (entity, deleted) -> this.evictCache(entity).map(evicted -> deleted));
    }

    public Mono<Tuple2<Tuple3<String, String, ULong>, Boolean>> hasAccess() {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> Mono.just(ca.isAuthenticated())
                        .flatMap(BooleanUtil::safeValueOfWithEmpty)
                        .switchIfEmpty(msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                ProcessorMessageResourceService.LOGIN_REQUIRED)),
                (ca, isAuthenticated) -> SecurityContextUtil.resolveAppAndClientCode(null, null),
                (ca, isAuthenticated, acTup) -> securityService
                        .appInheritance(acTup.getT1(), ca.getUrlClientCode(), acTup.getT2())
                        .map(clientCodes -> Mono.just(clientCodes.contains(acTup.getT2())))
                        .flatMap(BooleanUtil::safeValueOfWithEmpty)
                        .switchIfEmpty(msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                AbstractMongoMessageResourceService.FORBIDDEN_APP_ACCESS)),
                (ca, isAuthenticated, acTup, hasAppAccess) -> this.securityService
                        .isUserBeingManaged(ca.getUser().getId(), acTup.getT2())
                        .flatMap(BooleanUtil::safeValueOfWithEmpty)
                        .switchIfEmpty(msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                ProcessorMessageResourceService.INVALID_USER_FOR_CLIENT)),
                (ca, isAuthenticated, acTup, hasAppAccess, isUserManaged) -> Mono.just(Tuples.of(
                        Tuples.of(
                                acTup.getT1(),
                                acTup.getT2(),
                                ULongUtil.valueOf(ca.getUser().getId())),
                        hasAppAccess && isUserManaged)));
    }
}
