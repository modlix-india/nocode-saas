package com.fincity.saas.entity.processor.service;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

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
import com.fincity.saas.entity.processor.dao.BaseProcessorDAO;
import com.fincity.saas.entity.processor.dto.BaseDto;
import com.fincity.saas.entity.processor.dto.BaseProcessorDto;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

@Service
public abstract class BaseProcessorService<
                R extends UpdatableRecord<R>, D extends BaseProcessorDto<D>, O extends BaseProcessorDAO<R, D>>
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
                        ca.isAuthenticated() ? ULong.valueOf(ca.getUser().getId()) : null))
                .switchIfEmpty(msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        ProcessorMessageResourceService.INVALID_USER_FOR_CLIENT));
    }

    @Override
    protected Mono<D> updatableEntity(D entity) {

        return FlatMapUtil.flatMapMono(() -> this.read(entity.getId()), e -> {
            if (e.getVersion() != entity.getVersion())
                return this.msgService.throwMessage(
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

    public Mono<Tuple2<Tuple3<String, String, ULong>, Boolean>> hasAccess(
            String appCode, String clientCode, BigInteger userId) {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> SecurityContextUtil.resolveAppAndClientCode(appCode, clientCode),
                (ca, acTup) -> securityService
                        .appInheritance(acTup.getT1(), ca.getUrlClientCode(), acTup.getT2())
                        .map(clientCodes -> Mono.just(clientCodes.contains(acTup.getT2())))
                        .flatMap(BooleanUtil::safeValueOfWithEmpty)
                        .switchIfEmpty(msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                AbstractMongoMessageResourceService.FORBIDDEN_APP_ACCESS)),
                (ca, acTup, hasAppAccess) -> this.securityService
                        .isUserBeingManaged(userId, clientCode)
                        .flatMap(BooleanUtil::safeValueOfWithEmpty)
                        .switchIfEmpty(msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                ProcessorMessageResourceService.INVALID_USER_FOR_CLIENT)),
                (ca, acTup, hasAppAccess, isUserManaged) -> Mono.just(Tuples.of(
                        Tuples.of(
                                acTup.getT1(),
                                acTup.getT2(),
                                ULongUtil.valueOf(ca.getUser().getId())),
                        hasAppAccess && isUserManaged)));
    }

    public Mono<D> create(D entity, String appCode, String clientCode) {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> {
                    if (!ca.isAuthenticated())
                        return msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                ProcessorMessageResourceService.INVALID_USER_FOR_CLIENT);

                    return this.hasAccess(appCode, clientCode, ca.getUser().getId());
                },
                (ca, hasAccess) -> {
                    entity.setAppCode(hasAccess.getT1().getT1());
                    entity.setClientCode(hasAccess.getT1().getT2());
                    entity.setAddedByUserId(hasAccess.getT1().getT3());

                    return super.create(entity);
                });
    }

    public Mono<D> update(D entity, String appCode, String clientCode) {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> {
                    if (!ca.isAuthenticated())
                        return msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                ProcessorMessageResourceService.INVALID_USER_FOR_CLIENT);

                    return this.hasAccess(appCode, clientCode, ca.getUser().getId());
                },
                (ca, hasAccess) -> super.create(entity));
    }
}
