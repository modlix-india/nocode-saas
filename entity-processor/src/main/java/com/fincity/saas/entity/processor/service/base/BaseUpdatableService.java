package com.fincity.saas.entity.processor.service.base;

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
import org.springframework.util.MultiValueMap;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.flow.service.AbstractFlowUpdatableService;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.Case;
import com.fincity.saas.entity.processor.dao.base.BaseUpdatableDAO;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.enums.IEntitySeries;
import com.fincity.saas.entity.processor.functions.annotations.IgnoreGeneration;
import com.fincity.saas.entity.processor.model.base.BaseResponse;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;

import lombok.Getter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class BaseUpdatableService<R extends UpdatableRecord<R>, D extends BaseUpdatableDto<D>, O extends BaseUpdatableDAO<R, D>>
        extends AbstractFlowUpdatableService<R, ULong, D, O> implements IEntitySeries, IProcessorAccessService {

    @Getter(onMethod_ = @IgnoreGeneration)
    protected ProcessorMessageResourceService msgService;

    @Getter(onMethod_ = @IgnoreGeneration)
    protected IFeignSecurityService securityService;

    @Getter(onMethod_ = @IgnoreGeneration)
    protected CacheService cacheService;

    protected abstract String getCacheName();

    protected abstract boolean canOutsideCreate();

    protected Mono<D> checkEntity(D entity, ProcessorAccess access) {
        return Mono.just(entity);
    }

    protected <T> Mono<T> throwOutsideUserAccess(String action) {
        return this.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                ProcessorMessageResourceService.OUTSIDE_USER_ACCESS,
                action,
                this.getEntityName());
    }

    protected String getCacheName(String... entityNames) {
        return String.join("_", Stream.of(entityNames).filter(Objects::nonNull).toArray(String[]::new));
    }

    protected String getCacheName(Object... entityNames) {
        return String.join(
                ":",
                Stream.of(entityNames)
                        .filter(Objects::nonNull)
                        .map(Object::toString)
                        .toArray(String[]::new));
    }

    protected String getCacheKey(String... entityNames) {
        return String.join(":", Stream.of(entityNames).filter(Objects::nonNull).toArray(String[]::new));
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
        return Mono.zip(
                this.evictBaseCache(entity),
                this.evictAcCcCache(entity),
                (baseEvicted, acCcEvicted) -> baseEvicted && acCcEvicted);
    }

    protected Mono<Boolean> evictCaches(Flux<D> entities) {
        return entities.flatMap(this::evictCache).collectList().map(results -> results.stream()
                .allMatch(Boolean::booleanValue));
    }

    private Mono<Boolean> evictBaseCache(D entity) {
        return Mono.zip(
                this.cacheService.evict(this.getCacheName(), entity.getId()),
                this.cacheService.evict(this.getCacheName(), entity.getCode()),
                (idEvicted, codeEvicted) -> idEvicted && codeEvicted);
    }

    private Mono<Boolean> evictAcCcCache(D entity) {

        return Mono.zip(
                this.cacheService.evict(
                        this.getCacheName(),
                        this.getCacheKey(entity.getAppCode(), entity.getClientCode(), entity.getId())),
                this.cacheService.evict(
                        this.getCacheName(),
                        this.getCacheKey(entity.getAppCode(), entity.getClientCode(), entity.getCode())),
                (idEvicted, codeEvicted) -> idEvicted && codeEvicted);
    }

    @Autowired
    protected void setMsgService(ProcessorMessageResourceService msgService) {
        this.msgService = msgService;
    }

    @Autowired
    protected void setCacheService(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Autowired
    protected void setSecurityService(IFeignSecurityService securityService) {
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
                .switchIfEmpty(Mono.empty())
                .onErrorResume(e -> Mono.empty());
    }

    @Override
    protected Mono<D> updatableEntity(D entity) {

        return FlatMapUtil.flatMapMono(() -> this.readByIdInternal(entity.getId()), existing -> {
            if (entity.getName() != null && !entity.getName().isEmpty())
                existing.setName(entity.getName());
            existing.setDescription(entity.getDescription());
            existing.setTempActive(entity.isTempActive());
            existing.setActive(entity.isActive());
            return Mono.just(existing);
        });
    }

    @Override
    public Mono<D> create(D entity) {
        return this.hasAccess().flatMap(access -> this.create(access, entity));
    }

    @IgnoreGeneration
    protected Mono<D> create(ProcessorAccess access, D entity) {
        return this.checkEntity(entity, access).flatMap(e -> this.createInternal(access, e));
    }

    @IgnoreGeneration
    public Mono<D> createInternal(ProcessorAccess access, D entity) {

        if (!canOutsideCreate() && access.isOutsideUser())
            return this.throwOutsideUserAccess("create");

        if (entity.getName() == null || entity.getName().isEmpty())
            entity.setName(entity.getCode());

        entity.setAppCode(access.getAppCode());

        entity.setClientCode(
                access.isOutsideUser() ? access.getUserInherit().getManagedClientCode() : access.getClientCode());

        return super.create(entity);
    }

    @Override
    public Mono<D> read(ULong id) {
        return this.hasAccess().flatMap(access -> this.readById(access, id));
    }

    public Mono<D> read(String code) {
        return this.hasAccess().flatMap(access -> this.readByCode(access, code));
    }

    public Mono<Map<String, Object>> readEager(
            ULong id, List<String> tableFields, MultiValueMap<String, String> queryParams) {
        return this.hasAccess()
                .flatMap(access -> this.dao.readByIdAndAppCodeAndClientCodeEager(id, access, tableFields, queryParams));
    }

    public Mono<Map<String, Object>> readEager(
            String code, List<String> tableFields, MultiValueMap<String, String> queryParams) {
        return this.hasAccess()
                .flatMap(access -> this.dao.readByCodeAndAppCodeAndClientCodeEager(code, access, tableFields,
                        queryParams));
    }

    public Mono<Map<String, Object>> readEager(
            Identity identity, List<String> tableFields, MultiValueMap<String, String> queryParams) {
        return this.hasAccess()
                .flatMap(access -> this.dao.readByIdentityAndAppCodeAndClientCodeEager(
                        identity, access, tableFields, queryParams));
    }

    protected Mono<D> checkExistsByName(ProcessorAccess access, D entity) {
        return this.existsByName(access, entity.getId(), entity.getName())
                .flatMap(exists -> Boolean.TRUE.equals(exists)
                        ? msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                                ProcessorMessageResourceService.DUPLICATE_NAME_FOR_ENTITY,
                                entity.getName(),
                                this.getEntityName())
                        : Mono.just(entity));
    }

    private Mono<Boolean> existsByName(ProcessorAccess access, ULong neEntityId, String name) {
        return this.dao.existsByName(access.getAppCode(), access.getClientCode(), neEntityId, name);
    }

    @Override
    public Mono<Page<D>> readPageFilter(Pageable pageable, AbstractCondition condition) {
        return FlatMapUtil.flatMapMono(
                this::hasAccess,
                access -> this.dao.processorAccessCondition(condition, access),
                (access, pCondition) -> super.readPageFilter(pageable, pCondition));
    }

    public Mono<Page<Map<String, Object>>> readPageFilterEager(
            Pageable pageable,
            AbstractCondition condition,
            List<String> tableFields,
            MultiValueMap<String, String> queryParams) {

        return FlatMapUtil.flatMapMono(
                this::hasAccess,
                access -> this.dao.processorAccessCondition(condition, access),
                (access, pCondition) -> this.dao.readPageFilterEager(pageable, pCondition, tableFields, queryParams));
    }

    @Override
    public Flux<D> readAllFilter(AbstractCondition condition) {
        return this.hasAccess()
                .flatMap(access -> this.dao.processorAccessCondition(condition, access))
                .flatMapMany(super::readAllFilter);
    }

    @Override
    public Mono<D> update(D entity) {
        return this.hasAccess().flatMap(access -> this.update(access, entity));
    }

    @IgnoreGeneration
    public Mono<D> update(ProcessorAccess access, D entity) {
        return this.checkEntity(entity, access).flatMap(cEntity -> this.updateInternal(access, cEntity));
    }

    @Override
    public Mono<D> update(ULong key, Map<String, Object> fields) {
        if (key == null)
            return Mono.empty();

        return FlatMapUtil.flatMapMono(
                this::hasAccess,
                access -> this.read(key),
                (access, entity) -> this.updateInternal(access, key, fields));
    }

    @SuppressWarnings("unchecked")
    public Mono<D> updateByCode(String code, D entity) {
        return FlatMapUtil.flatMapMono(
                this::hasAccess,
                access -> this.readByCode(access, code).map(cEntity -> (D) entity.setId(cEntity.getId())),
                this::update);
    }

    @IgnoreGeneration
    public Mono<D> updateInternal(ProcessorAccess access, D entity) {

        if (!canOutsideCreate() && access.isOutsideUser())
            return this.throwOutsideUserAccess("update");

        return super.update(entity).flatMap(updated -> this.evictCache(updated).map(evicted -> updated));
    }

    protected Mono<D> updateInternalForOutsideUser(D entity) {
        return super.update(entity).flatMap(updated -> this.evictCache(updated).map(evicted -> updated));
    }

    protected Mono<D> updateInternal(ProcessorAccess access, ULong key, Map<String, Object> fields) {

        if (!canOutsideCreate() && access.isOutsideUser())
            return this.throwOutsideUserAccess("update");

        return super.update(key, fields)
                .flatMap(updated -> this.evictCache(updated).map(evicted -> updated));
    }

    protected <T> Mono<T> identityMissingError() {
        return this.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                ProcessorMessageResourceService.IDENTITY_MISSING,
                this.getEntityName());
    }

    protected <T> Mono<T> identityMissingError(String entityName) {
        return this.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                ProcessorMessageResourceService.IDENTITY_MISSING,
                entityName);
    }

    @IgnoreGeneration
    public Mono<D> readById(ProcessorAccess access, ULong id) {

        if (id == null)
            return this.identityMissingError();

        return this.readByIdInternal(access, id)
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        ProcessorMessageResourceService.IDENTITY_WRONG,
                        this.getEntityName(),
                        id));
    }

    private Mono<D> readByIdInternal(ULong id) {
        return this.cacheService.cacheValueOrGet(this.getCacheName(), () -> this.dao.readInternal(id), id);
    }

    private Mono<D> readByIdInternal(ProcessorAccess access, ULong id) {
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.dao.readInternal(access, id),
                this.getCacheKey(access.getAppCode(), access.getClientCode(), id));
    }

    @IgnoreGeneration
    public Mono<D> readByCode(ProcessorAccess access, String code) {

        if (code == null || code.isEmpty())
            return this.identityMissingError();

        return this.readByCodeInternal(access, code)
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        ProcessorMessageResourceService.IDENTITY_WRONG,
                        this.getEntityName(),
                        code));
    }

    private Mono<D> readByCodeInternal(ProcessorAccess access, String code) {
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.dao.readInternal(access, code),
                this.getCacheKey(access.getAppCode(), access.getClientCode(), code));
    }

    public Mono<D> readByIdentity(Identity identity) {
        return this.hasAccess().flatMap(access -> this.readByIdentity(access, identity));
    }

    @IgnoreGeneration
    public Mono<D> readByIdentity(ProcessorAccess access, Identity identity) {

        if (identity == null || identity.isNull())
            return this.identityMissingError();

        return identity.isId()
                ? this.readById(access, identity.getULongId())
                : this.readByCode(access, identity.getCode());
    }

    @IgnoreGeneration
    public Mono<Identity> checkAndUpdateIdentityWithAccess(ProcessorAccess access, Identity identity) {

        if (identity == null || identity.isNull())
            return this.identityMissingError();

        return identity.isId()
                ? this.readById(access, identity.getULongId()).map(BaseUpdatableDto::getIdentity)
                : this.readByCode(access, identity.getCode()).map(BaseUpdatableDto::getIdentity);
    }

    public Mono<Integer> deleteIdentity(Identity identity) {
        return this.readByIdentity(identity).flatMap(entity -> this.delete(entity.getId()));
    }

    public Mono<Integer> deleteByCode(String code) {
        return FlatMapUtil.flatMapMono(this::hasAccess, access -> this.readByCode(access, code), this::deleteInternal);
    }

    @Override
    public Mono<Integer> delete(ULong id) {
        return FlatMapUtil.flatMapMono(this::hasAccess, access -> this.read(id), this::deleteInternal);
    }

    protected Mono<Integer> deleteInternal(ProcessorAccess access, D entity) {

        if (!canOutsideCreate() && access.isOutsideUser())
            return this.throwOutsideUserAccess("delete");

        return super.delete(entity.getId())
                .flatMap(deleted -> this.evictCache(entity).map(evicted -> deleted));
    }

    public Mono<Integer> deleteMultiple(Collection<D> entities) {
        return this.dao
                .deleteMultiple(entities.stream().map(AbstractDTO::getId).toList())
                .flatMap(
                        deleted -> this.evictCaches(Flux.fromIterable(entities)).map(evicted -> deleted));
    }

    public Mono<BaseResponse> getBaseResponse(ULong id) {
        return this.hasAccess().flatMap(access -> this.readById(access, id)).map(BaseUpdatableDto::getBaseResponse);
    }

    public Mono<BaseResponse> getBaseResponse(String code) {
        return this.hasAccess().flatMap(access -> this.readByCode(access, code)).map(BaseUpdatableDto::getBaseResponse);
    }

    protected <T> Mono<T> throwMissingParam(String paramName) {
        return this.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                ProcessorMessageResourceService.MISSING_PARAMETERS,
                Case.TITLE.getConverter().apply(paramName),
                this.getEntityName());
    }

    protected <T> Mono<T> throwInvalidParam(String paramName) {
        return this.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                ProcessorMessageResourceService.INVALID_PARAMETERS,
                Case.TITLE.getConverter().apply(paramName),
                this.getEntityName());
    }
}
