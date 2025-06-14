package com.fincity.saas.entity.processor.service.base;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.flow.service.AbstractFlowUpdatableService;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.entity.processor.dao.base.BaseUpdatableDAO;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.enums.IEntitySeries;
import com.fincity.saas.entity.processor.model.base.BaseResponse;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

@Service
public abstract class BaseUpdatableService<
                R extends UpdatableRecord<R>, D extends BaseUpdatableDto<D>, O extends BaseUpdatableDAO<R, D>>
        extends AbstractFlowUpdatableService<R, ULong, D, O> implements IEntitySeries {

    protected ProcessorMessageResourceService msgService;
    protected CacheService cacheService;
    protected IFeignSecurityService securityService;

    protected abstract String getCacheName();

    protected String getCacheKey(String... entityNames) {
        return String.join(":", entityNames);
    }

    protected String getCacheKey(Object... entityNames) {
        return String.join(
                ":",
                Stream.of(entityNames)
                        .filter(Objects::nonNull)
                        .map(Object::toString)
                        .toArray(String[]::new));
    }

    protected Mono<Boolean> evictCache(D entity) {
        return this.cacheService
                .evict(this.getCacheName(), entity.getCode())
                .flatMap(evicted -> this.cacheService.evict(getCacheName(), entity.getId()));
    }

    protected Mono<Boolean> evictCaches(Flux<D> entities) {
        return entities.flatMap(this::evictCache).collectList().map(results -> results.stream()
                .allMatch(Boolean::booleanValue));
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
                        ca.isAuthenticated() ? ULong.valueOf(ca.getUser().getId()) : null));
    }

    @Override
    public Mono<D> read(ULong id) {
        return this.hasAccess()
                .flatMap(hasAccess -> this.dao.readByIdAndAppCodeAndClientCode(
                        id, hasAccess.getT1().getT1(), hasAccess.getT1().getT2()));
    }

    public Mono<D> read(String code) {
        return this.hasAccess()
                .flatMap(hasAccess -> this.dao.readByCodeAndAppCodeAndClientCode(
                        code, hasAccess.getT1().getT1(), hasAccess.getT1().getT2()));
    }

    public Mono<Map<String, Object>> readEager(
            ULong id, List<String> tableFields, Boolean eager, List<String> eagerFields) {
        return this.hasAccess()
                .flatMap(hasAccess -> this.dao.readByIdAndAppCodeAndClientCodeEager(
                        id, hasAccess.getT1().getT1(), hasAccess.getT1().getT2(), tableFields, eager, eagerFields));
    }

    public Mono<Map<String, Object>> readEager(
            String code, List<String> tableFields, Boolean eager, List<String> eagerFields) {
        return this.hasAccess()
                .flatMap(hasAccess -> this.dao.readByCodeAndAppCodeAndClientCodeEager(
                        code, hasAccess.getT1().getT1(), hasAccess.getT1().getT2(), tableFields, eager, eagerFields));
    }

    public Mono<D> readById(ULong id) {
        return this.dao
                .readInternal(id)
                .flatMap(value -> value != null
                        ? this.cacheService.cacheValueOrGet(this.getCacheName(), () -> Mono.just(value), id)
                        : Mono.empty());
    }

    public Mono<D> readByCode(String code) {
        return this.dao
                .readInternal(code)
                .flatMap(value -> value != null
                        ? this.cacheService.cacheValueOrGet(this.getCacheName(), () -> Mono.just(value), code)
                        : Mono.empty());
    }

    @Override
    public Mono<Page<D>> readPageFilter(Pageable pageable, AbstractCondition condition) {
        return this.hasAccess()
                .flatMap(accessInfo -> super.readPageFilter(
                        pageable, addAppCodeAndClientCodeToCondition(accessInfo.getT1(), condition)));
    }

    public Mono<Page<Map<String, Object>>> readPageFilterEager(
            Pageable pageable,
            AbstractCondition condition,
            List<String> tableFields,
            Boolean eager,
            List<String> eagerFields) {
        return this.hasAccess()
                .flatMap(accessInfo -> this.dao.readPageFilterEager(
                        pageable,
                        addAppCodeAndClientCodeToCondition(accessInfo.getT1(), condition),
                        tableFields,
                        eager,
                        eagerFields));
    }

    @Override
    public Flux<D> readAllFilter(AbstractCondition condition) {
        return this.hasAccess()
                .flatMapMany(hasAccess ->
                        super.readAllFilter(addAppCodeAndClientCodeToCondition(hasAccess.getT1(), condition)));
    }

    private AbstractCondition addAppCodeAndClientCodeToCondition(
            Tuple3<String, String, ULong> access, AbstractCondition condition) {
        if (condition == null || condition.isEmpty())
            return ComplexCondition.and(
                    FilterCondition.make("appCode", access.getT1()).setOperator(FilterConditionOperator.EQUALS),
                    FilterCondition.make("clientCode", access.getT2()).setOperator(FilterConditionOperator.EQUALS));

        return ComplexCondition.and(
                condition,
                FilterCondition.make("appCode", access.getT1()).setOperator(FilterConditionOperator.EQUALS),
                FilterCondition.make("clientCode", access.getT2()).setOperator(FilterConditionOperator.EQUALS));
    }

    @Override
    protected Mono<D> updatableEntity(D entity) {

        return FlatMapUtil.flatMapMono(() -> this.read(entity.getId()), existing -> {
            existing.setName(entity.getName());
            existing.setDescription(entity.getDescription());
            existing.setTempActive(entity.isTempActive());
            existing.setActive(entity.isActive());
            return Mono.just(existing);
        });
    }

    public Flux<D> readByCodes(List<String> codes) {
        return Flux.fromIterable(codes).flatMap(this::readByCode);
    }

    public Mono<D> readIdentity(Identity identity) {
        return FlatMapUtil.flatMapMono(this::hasAccess, hasAccess -> this.readIdentityInternal(identity));
    }

    public Mono<D> readByIdInternal(ULong id) {

        if (id == null)
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.IDENTITY_MISSING,
                    this.getEntityName());

        return this.readById(id)
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        ProcessorMessageResourceService.IDENTITY_WRONG,
                        this.getEntityName(),
                        id));
    }

    public Mono<D> readIdentityInternal(Identity identity) {

        if (identity == null || identity.isNull())
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.IDENTITY_MISSING,
                    this.getEntityName());

        return (identity.isCode()
                        ? this.readByCode(identity.getCode())
                                .switchIfEmpty(this.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        ProcessorMessageResourceService.IDENTITY_WRONG,
                                        this.getEntityName(),
                                        identity.getCode()))
                        : this.readById(identity.getULongId()))
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        ProcessorMessageResourceService.IDENTITY_WRONG,
                        this.getEntityName(),
                        identity.getId()));
    }

    public Mono<D> readIdentityBasicInternal(Identity identity) {
        if (identity == null || identity.isNull()) return Mono.empty();

        return (identity.isCode()
                        ? this.readByCode(identity.getCode()).switchIfEmpty(Mono.empty())
                        : this.readById(ULongUtil.valueOf(identity.getId())))
                .switchIfEmpty(Mono.empty());
    }

    public Mono<Identity> updateIdentity(Identity identity) {

        if (identity.isId()) return Mono.just(identity);

        return this.readByCode(identity.getCode())
                .map(entity -> identity.setId(entity.getId().toBigInteger()));
    }

    public Mono<Identity> checkAndUpdateIdentity(Identity identity) {

        if (identity == null || identity.isNull())
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.IDENTITY_MISSING);

        if (identity.isId())
            return this.readById(ULongUtil.valueOf(identity.getId()))
                    .map(entity -> identity)
                    .switchIfEmpty(this.msgService.throwMessage(
                            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                            ProcessorMessageResourceService.IDENTITY_WRONG,
                            this.getEntityName(),
                            identity.getId()));

        return this.readByCode(identity.getCode())
                .map(entity -> identity.setId(entity.getId().toBigInteger()))
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        ProcessorMessageResourceService.IDENTITY_WRONG,
                        this.getEntityName(),
                        identity.getCode()));
    }

    public Mono<Integer> deleteIdentity(Identity identity) {
        return this.readIdentityInternal(identity).flatMap(entity -> this.delete(entity.getId()));
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

    @Override
    public Mono<Integer> delete(ULong id) {
        return FlatMapUtil.flatMapMono(
                () -> this.read(id), entity -> super.delete(id), (entity, deleted) -> this.evictCache(entity)
                        .map(evicted -> deleted));
    }

    public Mono<Integer> deleteMultiple(Collection<D> entities) {
        return this.dao
                .deleteMultiple(entities.stream().map(AbstractDTO::getId).toList())
                .flatMap(
                        deleted -> this.evictCaches(Flux.fromIterable(entities)).map(evicted -> deleted));
    }

    public Mono<Integer> deleteMultiple(Flux<D> entities) {
        return this.dao.deleteMultiple(entities.map(AbstractDTO::getId)).flatMap(deleted -> this.evictCaches(entities)
                .map(evicted -> deleted));
    }

    public Mono<Tuple2<Tuple3<String, String, ULong>, Boolean>> hasAccess() {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> Mono.just(ca.isAuthenticated())
                        .flatMap(BooleanUtil::safeValueOfWithEmpty)
                        .switchIfEmpty(msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                ProcessorMessageResourceService.LOGIN_REQUIRED)),
                (ca, isAuthenticated) -> this.getAppClientUserAccessInfo(ca));
    }

    public Mono<Tuple2<Tuple3<String, String, ULong>, Boolean>> hasPublicAccess() {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication, this::getAppClientUserAccessInfo);
    }

    public Mono<BaseResponse> getBaseResponse(ULong id) {
        return this.readById(id).map(BaseUpdatableDto::toBaseResponse);
    }

    public Mono<BaseResponse> getBaseResponse(String code) {
        return this.readByCode(code).map(BaseUpdatableDto::toBaseResponse);
    }

    private Mono<Tuple2<Tuple3<String, String, ULong>, Boolean>> getAppClientUserAccessInfo(ContextAuthentication ca) {
        return FlatMapUtil.flatMapMono(
                () -> SecurityContextUtil.resolveAppAndClientCode(null, null),
                acTup -> securityService
                        .appInheritance(acTup.getT1(), ca.getUrlClientCode(), acTup.getT2())
                        .map(clientCodes -> clientCodes.contains(acTup.getT2()))
                        .flatMap(BooleanUtil::safeValueOfWithEmpty)
                        .switchIfEmpty(msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                ProcessorMessageResourceService.FORBIDDEN_APP_ACCESS,
                                acTup.getT2())),
                (acTup, hasAppAccess) -> this.securityService
                        .isUserBeingManaged(ca.getUser().getId(), acTup.getT2())
                        .flatMap(BooleanUtil::safeValueOfWithEmpty)
                        .switchIfEmpty(msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                ProcessorMessageResourceService.INVALID_USER_FOR_CLIENT,
                                ca.getUser().getId(),
                                acTup.getT2())),
                (acTup, hasAppAccess, isUserManaged) -> Mono.just(Tuples.of(
                        Tuples.of(
                                acTup.getT1(),
                                acTup.getT2(),
                                ULongUtil.valueOf(ca.getUser().getId())),
                        hasAppAccess && isUserManaged)));
    }
}
