package com.fincity.saas.entity.processor.service.base;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.flow.service.AbstractFlowUpdatableService;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.entity.processor.dao.base.BaseUpdatableDAO;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.enums.IEntitySeries;
import com.fincity.saas.entity.processor.model.base.BaseResponse;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.Getter;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class BaseUpdatableService<
                R extends UpdatableRecord<R>, D extends BaseUpdatableDto<D>, O extends BaseUpdatableDAO<R, D>>
        extends AbstractFlowUpdatableService<R, ULong, D, O> implements IEntitySeries, IProcessorAccessService {

    @Getter
    protected ProcessorMessageResourceService msgService;

    @Getter
    protected IFeignSecurityService securityService;

    @Getter
    protected CacheService cacheService;

    protected abstract String getCacheName();

    protected abstract boolean canOutsideCreate();

    protected <T> Mono<T> throwOutsideUserAccess(String action) {
        return this.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                ProcessorMessageResourceService.OUTSIDE_USER_ACCESS,
                action,
                this.getEntityName());
    }

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
    public void setMsgService(ProcessorMessageResourceService msgService) {
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

    public Mono<D> createInternal(ProcessorAccess access, D entity) {

        if (!canOutsideCreate() && access.isOutsideUser()) return this.throwOutsideUserAccess("create");

        if (entity.getName() == null || entity.getName().isEmpty()) entity.setName(entity.getCode());

        entity.setAppCode(access.getAppCode());

        entity.setClientCode(
                access.isOutsideUser() ? access.getUserInherit().getManagedClientCode() : access.getClientCode());

        return super.create(entity);
    }

    public Mono<D> updateInternal(ProcessorAccess access, D entity) {

        if (!canOutsideCreate() && access.isOutsideUser()) return this.throwOutsideUserAccess("update");

        return super.update(entity);
    }

    public Mono<D> updateInternal(ProcessorAccess access, ULong key, Map<String, Object> fields) {

        if (!canOutsideCreate() && access.isOutsideUser()) return this.throwOutsideUserAccess("update");

        return super.update(key, fields);
    }

    public Mono<Integer> deleteInternal(ProcessorAccess access, D entity) {

        if (!canOutsideCreate() && access.isOutsideUser()) return this.throwOutsideUserAccess("delete");

        return super.delete(entity.getId())
                .flatMap(deleted -> this.evictCache(entity).map(evicted -> deleted));
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
                .flatMap(access ->
                        this.dao.readByCodeAndAppCodeAndClientCodeEager(code, access, tableFields, queryParams));
    }

    public Mono<Map<String, Object>> readEager(
            Identity identity, List<String> tableFields, MultiValueMap<String, String> queryParams) {
        return this.hasAccess()
                .flatMap(access -> this.dao.readByIdentityAndAppCodeAndClientCodeEager(
                        identity, access, tableFields, queryParams));
    }

    public Mono<D> readById(ULong id) {
        return this.cacheService.cacheValueOrGet(this.getCacheName(), () -> this.dao.readInternal(id), id);
    }

    public Mono<D> readByCode(String code) {
        return this.cacheService.cacheValueOrGet(this.getCacheName(), () -> this.dao.readInternal(code), code);
    }

    public Mono<D> readById(ProcessorAccess access, ULong id) {
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.dao.readInternal(access, id),
                this.getCacheKey(access.getAppCode(), access.getClientCode(), id));
    }

    public Mono<D> readByCode(ProcessorAccess access, String code) {
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.dao.readInternal(access, code),
                this.getCacheKey(access.getAppCode(), access.getClientCode(), code));
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
    public Mono<D> update(ULong key, Map<String, Object> fields) {
        return super.update(key, fields)
                .flatMap(updated -> this.evictCache(updated).map(evicted -> updated));
    }

    @Override
    public Mono<D> update(D entity) {
        return super.update(entity).flatMap(updated -> this.evictCache(updated).map(evicted -> updated));
    }

    @Override
    protected Mono<D> updatableEntity(D entity) {

        return FlatMapUtil.flatMapMono(() -> this.read(entity.getId()), existing -> {
            if (entity.getName() != null && !entity.getName().isEmpty()) existing.setName(entity.getName());
            existing.setDescription(entity.getDescription());
            existing.setTempActive(entity.isTempActive());
            existing.setActive(entity.isActive());
            return Mono.just(existing);
        });
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

    public Mono<D> readByIdInternal(ULong id) {

        if (id == null) return this.identityMissingError();

        return this.readById(id)
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        ProcessorMessageResourceService.IDENTITY_WRONG,
                        this.getEntityName(),
                        id));
    }

    public Mono<D> readIdentityInternal(Identity identity) {

        if (identity == null || identity.isNull()) return this.identityMissingError();

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

    public Mono<D> readIdentityWithAccess(Identity identity) {
        return this.hasAccess().flatMap(access -> this.readIdentityWithAccess(access, identity));
    }

    public Mono<D> readIdentityWithAccess(ProcessorAccess access, Identity identity) {

        if (identity == null || identity.isNull()) return this.identityMissingError();

        return identity.isCode()
                ? this.readByCode(access, identity.getCode())
                        .switchIfEmpty(this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                ProcessorMessageResourceService.IDENTITY_WRONG,
                                this.getEntityName(),
                                identity.getCode()))
                : this.readById(access, identity.getULongId())
                        .switchIfEmpty(this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                ProcessorMessageResourceService.IDENTITY_WRONG,
                                this.getEntityName(),
                                identity.getId()));
    }

    public Mono<D> readIdentityWithAccessEmpty(ProcessorAccess access, Identity identity) {
        if (identity == null || identity.isNull()) return Mono.empty();

        return identity.isCode()
                ? this.readByCode(access, identity.getCode()).switchIfEmpty(Mono.empty())
                : this.readById(access, ULongUtil.valueOf(identity.getId())).switchIfEmpty(Mono.empty());
    }

    public Mono<Identity> updateIdentity(Identity identity) {

        if (identity.isId()) return Mono.just(identity);

        return this.readByCode(identity.getCode())
                .map(entity -> identity.setId(entity.getId().toBigInteger()));
    }

    public Mono<Identity> checkAndUpdateIdentity(Identity identity) {

        if (identity == null || identity.isNull()) return this.identityMissingError();

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

    public Mono<Identity> checkAndUpdateIdentityWithAccess(Identity identity) {
        return this.hasAccess().flatMap(access -> this.checkAndUpdateIdentityWithAccess(access, identity));
    }

    public Mono<Identity> checkAndUpdateIdentityWithAccess(ProcessorAccess access, Identity identity) {

        if (identity == null || identity.isNull()) return this.identityMissingError();

        if (identity.isId())
            return this.readById(access, ULongUtil.valueOf(identity.getId()))
                    .map(entity -> identity)
                    .switchIfEmpty(this.msgService.throwMessage(
                            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                            ProcessorMessageResourceService.IDENTITY_WRONG,
                            this.getEntityName(),
                            identity.getId()));

        return this.readByCode(access, identity.getCode())
                .map(entity -> identity.setId(entity.getId().toBigInteger()))
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        ProcessorMessageResourceService.IDENTITY_WRONG,
                        this.getEntityName(),
                        identity.getCode()));
    }

    public Mono<Integer> deleteIdentity(Identity identity) {
        return this.readIdentityWithAccess(identity).flatMap(entity -> this.delete(entity.getId()));
    }

    @SuppressWarnings("unchecked")
    public Mono<D> updateByCode(String code, D entity) {
        return FlatMapUtil.flatMapMono(
                () -> this.readByCode(code).map(cEntity -> (D) entity.setId(cEntity.getId())),
                this::update,
                (e, updated) -> this.evictCache(updated).map(evicted -> updated));
    }

    public Mono<Integer> deleteByCode(String code) {
        return FlatMapUtil.flatMapMono(
                this::hasAccess,
                access -> this.readByCode(access, code),
                this::deleteInternal,
                (access, entity, deleted) -> this.evictCache(entity).map(evicted -> deleted));
    }

    @Override
    public Mono<Integer> delete(ULong id) {
        return FlatMapUtil.flatMapMono(
                this::hasAccess,
                access -> this.read(id),
                this::deleteInternal,
                (access, entity, deleted) -> this.evictCache(entity).map(evicted -> deleted));
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
                this.getEntityName(),
                paramName);
    }

    protected <T> Mono<T> throwInvalidParam(String paramName) {
        return this.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                ProcessorMessageResourceService.INVALID_PARAMETERS,
                this.getEntityName(),
                paramName);
    }
}
