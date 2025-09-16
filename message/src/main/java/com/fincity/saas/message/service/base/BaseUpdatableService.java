package com.fincity.saas.message.service.base;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.message.dao.base.BaseUpdatableDAO;
import com.fincity.saas.message.dto.base.BaseUpdatableDto;
import com.fincity.saas.message.enums.IMessageSeries;
import com.fincity.saas.message.model.base.BaseResponse;
import com.fincity.saas.message.model.common.Identity;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.service.MessageResourceService;
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
        extends AbstractJOOQUpdatableDataService<R, ULong, D, O> implements IMessageSeries, IMessageAccessService {

    @Getter
    protected MessageResourceService msgService;

    @Getter
    protected IFeignSecurityService securityService;

    @Getter
    protected CacheService cacheService;

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

        return FlatMapUtil.flatMapMono(
                this::hasAccess,
                access -> Mono.zip(
                        this.cacheService.evict(
                                this.getCacheName(),
                                this.getCacheKey(
                                        access.getAppCode(),
                                        access.getClientCode(),
                                        access.getUserId(),
                                        entity.getId())),
                        this.cacheService.evict(
                                this.getCacheName(),
                                this.getCacheKey(
                                        access.getAppCode(),
                                        access.getClientCode(),
                                        access.getUserId(),
                                        entity.getCode())),
                        (idEvicted, codeEvicted) -> idEvicted && codeEvicted));
    }

    @Autowired
    public void setMessageResourceService(MessageResourceService msgService) {
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

    public Mono<D> createInternal(MessageAccess access, D entity) {

        if (access.getClientCode().equals("SYSTEM"))
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    "SYSTEM Adding Message is not allowed");

        entity.setAppCode(access.getAppCode());
        entity.setClientCode(access.getClientCode());
        entity.setUserId(access.getUserId());

        return super.create(entity);
    }

    public Mono<D> createInternal(MessageAccess publicAccess, ULong userId, D entity) {

        entity.setAppCode(publicAccess.getAppCode());
        entity.setClientCode(publicAccess.getClientCode());
        entity.setUserId(userId);

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

    public Mono<D> readById(MessageAccess access, ULong id) {
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.dao.readInternal(access, id),
                this.getCacheKey(access.getAppCode(), access.getClientCode(), access.getUserId(), id));
    }

    public Mono<D> readByCode(MessageAccess access, String code) {
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.dao.readInternal(access, code),
                this.getCacheKey(access.getAppCode(), access.getClientCode(), access.getUserId(), code));
    }

    @Override
    public Mono<Page<D>> readPageFilter(Pageable pageable, AbstractCondition condition) {
        return FlatMapUtil.flatMapMono(
                this::hasAccess,
                access -> this.dao.messageAccessCondition(condition, access),
                (access, pCondition) -> super.readPageFilter(pageable, pCondition));
    }

    public Mono<Page<Map<String, Object>>> readPageFilterEager(
            Pageable pageable,
            AbstractCondition condition,
            List<String> tableFields,
            MultiValueMap<String, String> queryParams) {

        return FlatMapUtil.flatMapMono(
                this::hasAccess,
                access -> this.dao.messageAccessCondition(condition, access),
                (access, pCondition) -> this.dao.readPageFilterEager(pageable, pCondition, tableFields, queryParams));
    }

    @Override
    public Flux<D> readAllFilter(AbstractCondition condition) {
        return this.hasAccess()
                .flatMap(access -> this.dao.messageAccessCondition(condition, access))
                .flatMapMany(super::readAllFilter);
    }

    @Override
    protected Mono<D> updatableEntity(D entity) {
        return FlatMapUtil.flatMapMono(() -> this.read(entity.getId()), existing -> {
            existing.setActive(entity.isActive());
            return Mono.just(existing);
        });
    }

    protected <T> Mono<T> identityMissingError() {
        return this.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                MessageResourceService.IDENTITY_MISSING,
                this.getMessageName());
    }

    protected <T> Mono<T> identityMissingError(String entityName) {
        return this.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                MessageResourceService.IDENTITY_MISSING,
                entityName);
    }

    public Mono<D> readByIdInternal(ULong id) {

        if (id == null) return this.identityMissingError();

        return this.readById(id)
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        MessageResourceService.IDENTITY_WRONG,
                        this.getMessageName(),
                        id));
    }

    public Mono<D> readIdentityInternal(Identity identity) {

        if (identity == null || identity.isNull()) return this.identityMissingError();

        return (identity.isCode()
                        ? this.readByCode(identity.getCode())
                                .switchIfEmpty(this.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        MessageResourceService.IDENTITY_WRONG,
                                        this.getMessageName(),
                                        identity.getCode()))
                        : this.readById(identity.getULongId()))
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        MessageResourceService.IDENTITY_WRONG,
                        this.getMessageName(),
                        identity.getId()));
    }

    public Mono<D> readIdentityWithAccess(Identity identity) {
        return this.hasAccess().flatMap(access -> this.readIdentityWithAccess(access, identity));
    }

    public Mono<D> readIdentityWithAccess(MessageAccess access, Identity identity) {

        if (identity == null || identity.isNull()) return this.identityMissingError();

        return identity.isCode()
                ? this.readByCode(access, identity.getCode())
                        .switchIfEmpty(this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                MessageResourceService.IDENTITY_WRONG,
                                this.getMessageName(),
                                identity.getCode()))
                : this.readById(access, identity.getULongId())
                        .switchIfEmpty(this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                MessageResourceService.IDENTITY_WRONG,
                                this.getMessageName(),
                                identity.getId()));
    }

    public Mono<D> readIdentityWithAccessEmpty(MessageAccess access, Identity identity) {
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
                            MessageResourceService.IDENTITY_WRONG,
                            this.getMessageName(),
                            identity.getId()));

        return this.readByCode(identity.getCode())
                .map(entity -> identity.setId(entity.getId().toBigInteger()))
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        MessageResourceService.IDENTITY_WRONG,
                        this.getMessageName(),
                        identity.getCode()));
    }

    public Mono<Identity> checkAndUpdateIdentityWithAccess(Identity identity) {
        return this.hasAccess().flatMap(access -> this.checkAndUpdateIdentityWithAccess(access, identity));
    }

    public Mono<Identity> checkAndUpdateIdentityWithAccess(MessageAccess access, Identity identity) {

        if (identity == null || identity.isNull()) return this.identityMissingError();

        if (identity.isId())
            return this.readById(access, ULongUtil.valueOf(identity.getId()))
                    .map(entity -> identity)
                    .switchIfEmpty(this.msgService.throwMessage(
                            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                            MessageResourceService.IDENTITY_WRONG,
                            this.getMessageName(),
                            identity.getId()));

        return this.readByCode(access, identity.getCode())
                .map(entity -> identity.setId(entity.getId().toBigInteger()))
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        MessageResourceService.IDENTITY_WRONG,
                        this.getMessageName(),
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
                (access, entity) -> this.dao.deleteByCode(code),
                (access, entity, deleted) -> this.evictCache(entity).map(evicted -> deleted));
    }

    @Override
    public Mono<Integer> delete(ULong id) {
        return FlatMapUtil.flatMapMono(
                () -> this.read(id), entity -> super.delete(id), (entity, deleted) -> this.evictCache(entity)
                        .map(evicted -> deleted));
    }

    public Mono<BaseResponse> getBaseResponse(ULong id) {
        return this.hasAccess().flatMap(access -> this.readById(access, id)).map(BaseUpdatableDto::getBaseResponse);
    }

    public Mono<BaseResponse> getBaseResponse(String code) {
        return this.hasAccess().flatMap(access -> this.readByCode(access, code)).map(BaseUpdatableDto::getBaseResponse);
    }
}
