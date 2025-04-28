package com.fincity.security.service.policy;

import java.util.Map;
import java.util.Objects;

import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.policy.AbstractPolicyDao;
import com.fincity.security.dto.policy.AbstractPolicy;
import com.fincity.security.service.AppService;
import com.fincity.security.service.ClientHierarchyService;
import com.fincity.security.service.ClientService;
import com.fincity.security.service.SecurityMessageResourceService;

import lombok.Getter;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
public abstract class AbstractPolicyService<R extends UpdatableRecord<R>, D extends AbstractPolicy, O extends AbstractPolicyDao<R, D>>
        extends AbstractJOOQUpdatableDataService<R, ULong, D, O> {

    protected static final ULong DEFAULT_POLICY_ID = ULong.MIN;
    protected final SecurityMessageResourceService securityMessageResourceService;
    private final CacheService cacheService;

    @Getter
    private ClientService clientService;

    @Getter
    private ClientHierarchyService clientHierarchyService;

    @Getter
    private AppService appService;

    protected AbstractPolicyService(SecurityMessageResourceService securityMessageResourceService,
                                    CacheService cacheService) {
        this.securityMessageResourceService = securityMessageResourceService;
        this.cacheService = cacheService;
    }

    @Autowired
    public void setClientService(@Lazy ClientService clientService) {
        this.clientService = clientService;
    }

    @Autowired
    public void setClientHierarchyService(ClientHierarchyService clientHierarchyService) {
        this.clientHierarchyService = clientHierarchyService;
    }

    @Autowired
    public void setAppService(@Lazy AppService appService) {
        this.appService = appService;
    }

    protected abstract String getPolicyName();

    public abstract String getPolicyCacheName();

    protected abstract Mono<D> getDefaultPolicy();

    public Mono<Boolean> policyBadRequestException(String messageId, Object... params) {
        return securityMessageResourceService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                messageId, params);
    }

    private String getCacheKeys(ULong clientId, ULong appId) {
        return clientId + ":" + appId;
    }

    @PreAuthorize("hasAuthority('Authorities.Application_CREATE')")
    @Override
    public Mono<D> create(D entity) {

        return FlatMapUtil.flatMapMono(

                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> this.updateClientIdAndAppId(ca, entity),

                        (ca, uEntity) -> this.canUpdatePolicy(ca, uEntity.getAppId())
                                .flatMap(BooleanUtil::safeValueOfWithEmpty),

                        (ca, uEntity, canCreate) -> super.create(uEntity),

                        (ca, uEntity, canCreate, created) -> cacheService.evict(getPolicyCacheName(),
                                getCacheKeys(created.getClientId(), created.getAppId())),

                        (ca, uEntity, canCreate, created, evicted) -> Mono.just(created))
                .switchIfEmpty(securityMessageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        SecurityMessageResourceService.FORBIDDEN_CREATE, getPolicyName()));
    }

    private Mono<D> updateClientIdAndAppId(ContextAuthentication ca, D entity) {

        return FlatMapUtil.flatMapMonoWithNull(

                () -> this.appService.getAppByCode(ca.getUrlAppCode()),

                app -> {

                    entity.initDefaults();

                    if (entity.getAppId() == null)
                        entity.setAppId(app.getId());

                    if (entity.getClientId() == null)
                        entity.setClientId(ULongUtil.valueOf(ca.getLoggedInFromClientId()));

                    return Mono.just(entity);
                },
                (app, uEntity) -> this.getClientAppPolicyInternal(uEntity.getClientId(), uEntity.getAppId()),

                (app, uEntity, policy) -> {

                    if (policy != null && !this.isDefaultPolicy(policy))
                        return securityMessageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                SecurityMessageResourceService.FORBIDDEN_CREATE, getPolicyName());

                    return Mono.just(uEntity);
                });
    }

    private boolean isDefaultPolicy(D policy) {
        return policy.getId() == null || policy.getId().equals(DEFAULT_POLICY_ID);
    }

    @PreAuthorize("hasAuthority('Authorities.Application_READ')")
    @Override
    public Mono<D> read(ULong id) {
        return super.read(id);
    }

    @PreAuthorize("hasAuthority('Authorities.Application_UPDATE')")
    @Override
    public Mono<D> update(ULong key, Map<String, Object> fields) {

        return FlatMapUtil.flatMapMono(

                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> key != null ? this.read(key)
                                : this.getClientAppPolicy(ca.getLoggedInFromClientCode(), ca.getUrlAppCode()),

                        (ca, entity) -> this.canUpdatePolicy(ca, entity.getAppId()).flatMap(BooleanUtil::safeValueOfWithEmpty),

                        (ca, entity, canUpdate) -> this.dao.canBeUpdated(key).flatMap(BooleanUtil::safeValueOfWithEmpty),

                        (ca, entity, canUpdate, canEntityUpdate) -> super.update(key, fields),

                        (ca, entity, canUpdate, canEntityUpdate, updated) -> cacheService.evict(getPolicyCacheName(),
                                getCacheKeys(updated.getClientId(), updated.getAppId())).<D>map(evicted -> updated))
                .switchIfEmpty(securityMessageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        SecurityMessageResourceService.FORBIDDEN_CREATE, getPolicyName()));
    }

    @PreAuthorize("hasAuthority('Authorities.Application_UPDATE')")
    @Override
    public Mono<D> update(D entity) {

        return FlatMapUtil.flatMapMono(

                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> this.dao.readById(entity.getId()),

                        (ca, uEntity) -> this.canUpdatePolicy(ca, uEntity.getAppId())
                                .flatMap(BooleanUtil::safeValueOfWithEmpty),

                        (ca, uEntity, canUpdate) -> this.dao.canBeUpdated(uEntity.getId())
                                .flatMap(BooleanUtil::safeValueOfWithEmpty),

                        (ca, uEntity, canUpdate, canEntityUpdate) -> super.update(entity),

                        (ca, uEntity, canUpdate, canEntityUpdate, updated) -> cacheService.evict(getPolicyCacheName(),
                                getCacheKeys(uEntity.getClientId(), uEntity.getAppId())).<D>map(evicted -> updated))
                .switchIfEmpty(securityMessageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        SecurityMessageResourceService.FORBIDDEN_CREATE, getPolicyName()));
    }

    @PreAuthorize("hasAuthority('Authorities.Application_DELETE')")
    @Override
    public Mono<Integer> delete(ULong id) {

        return FlatMapUtil.flatMapMono(

                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> id != null ? this.read(id)
                                : this.getClientAppPolicy(ca.getLoggedInFromClientCode(), ca.getUrlAppCode()),

                        (ca, entity) -> this.canUpdatePolicy(ca, entity.getAppId()).flatMap(BooleanUtil::safeValueOfWithEmpty),

                        (ca, entity, canDelete) -> this.dao.canBeUpdated(entity.getId())
                                .flatMap(BooleanUtil::safeValueOfWithEmpty),

                        (ca, entity, canDelete, canEntityDelete) -> super.delete(id),

                        (ca, entity, canDelete, canEntityDelete, deleted) -> cacheService.evict(getPolicyCacheName(),
                                getCacheKeys(entity.getClientId(), entity.getAppId())).<Integer>map(evicted -> deleted))
                .switchIfEmpty(securityMessageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        SecurityMessageResourceService.FORBIDDEN_CREATE, getPolicyName()));
    }

    private Mono<Boolean> canUpdatePolicy(ContextAuthentication ca, ULong appId) {

        ULong loggedInClientId = ULongUtil.valueOf(ca.getLoggedInFromClientId());

        return FlatMapUtil.flatMapMono(

                        () -> this.appService.getAppById(appId),

                        app -> FlatMapUtil.flatMapMonoConsolidate(
                                () -> this.clientService.isBeingManagedBy(loggedInClientId, app.getClientId()),
                                isManaged -> this.appService.hasWriteAccess(appId, loggedInClientId),
                                (isManaged, hasEditAccess) -> Mono.just(ca.isSystemClient())),

                        (app, managedOrEdit) -> Mono
                                .just(managedOrEdit.getT1() || managedOrEdit.getT2() || managedOrEdit.getT3()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractPolicyService.canUpdatePolicy"))
                .switchIfEmpty(Mono.just(Boolean.FALSE));
    }

    public Mono<D> getClientAppPolicy(String clientCode, String appCode) {
        return this.getClientAndAppId(clientCode, appCode)
                .flatMap(clientAppIds -> this.getClientAppPolicy(clientAppIds.getT1(), clientAppIds.getT2()));
    }

    private Mono<Tuple2<ULong, ULong>> getClientAndAppId(String clientCode, String appCode) {
        return FlatMapUtil.flatMapMono(
                () -> clientService.getClientId(clientCode),
                clientId -> appService.getAppByCode(appCode),
                (clientId, app) -> Mono.just(Tuples.of(clientId, app.getId())));
    }

    public Mono<D> getClientAppPolicy(ULong clientId, ULong appId) {

        return this.clientHierarchyService.getClientHierarchyIds(clientId)
                .flatMap(
                        client -> this.getClientAppPolicyInternal(client, appId)
                                .filter(Objects::nonNull))
                .next()
                .switchIfEmpty(this.getDefaultPolicy()).log();
    }

    private Mono<D> getClientAppPolicyInternal(ULong clientId, ULong appId) {
        return this.cacheService.cacheEmptyValueOrGet(this.getPolicyCacheName(),
                () -> this.dao.getClientAppPolicy(clientId, appId), getCacheKeys(clientId, appId));
    }

    public Mono<String> generatePolicyPassword(ULong clientId, ULong appId) {
        return this.getClientAppPolicy(clientId, appId).map(AbstractPolicy::generate);
    }
}
