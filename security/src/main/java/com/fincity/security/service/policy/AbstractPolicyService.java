package com.fincity.security.service.policy;

import java.util.HashMap;
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

	protected static final ULong DEFAULT_POLICY_ID = ULong.MIN;

	protected abstract String getPolicyName();

	public abstract String getPolicyCacheName();

	protected abstract Mono<D> getDefaultPolicy();

	public Mono<Boolean> policyBadRequestException(String messageId, Object... params) {
		return securityMessageResourceService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
				messageId, params);
	}

	@PreAuthorize("hasAuthority('Authorities.Application_CREATE')")
	@Override
	public Mono<D> create(D entity) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.canUpdatePolicy(ca, entity.getAppId()).flatMap(BooleanUtil::safeValueOfWithEmpty),

				(ca, canCreate) -> super.create(entity),

				(ca, canCreate, created) -> cacheService.evict(getPolicyCacheName(),
						getCacheKeys(created.getClientId(), created.getAppId())),

				(ca, canCreate, created, evicted) -> Mono.just(created))
				.switchIfEmpty(securityMessageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
						SecurityMessageResourceService.FORBIDDEN_CREATE, getPolicyName()));
	}

	@PreAuthorize("hasAuthority('Authorities.Application_READ')")
	@Override
	public Mono<D> read(ULong id) {
		return super.read(id);
	}

	@Override
	protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {

		if (fields == null || key == null)
			return Mono.just(new HashMap<>());

		fields.remove("clientId");
		fields.remove("appId");
		fields.remove("createdAt");
		fields.remove("createdBy");

		return Mono.just(fields);
	}

	@PreAuthorize("hasAuthority('Authorities.Application_UPDATE')")
	@Override
	public Mono<D> update(ULong key, Map<String, Object> fields) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.read(key),

				(ca, entity) -> this.canUpdatePolicy(ca, entity.getAppId()).flatMap(BooleanUtil::safeValueOfWithEmpty),

				(ca, entity, canUpdate) -> this.dao.canBeUpdated(key).flatMap(BooleanUtil::safeValueOfWithEmpty),

				(ca, entity, canUpdate, canEntityUpdate) -> super.update(key, fields),

				(ca, entity, canUpdate, canEntityUpdate, updated) -> cacheService.evict(getPolicyCacheName(),
						getCacheKeys(updated.getClientId(), updated.getAppId())).map(evicted -> updated))
				.switchIfEmpty(securityMessageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
						SecurityMessageResourceService.FORBIDDEN_CREATE, getPolicyName()));
	}

	@PreAuthorize("hasAuthority('Authorities.Application_UPDATE')")
	@Override
	public Mono<D> update(D entity) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.canUpdatePolicy(ca, entity.getAppId()).flatMap(BooleanUtil::safeValueOfWithEmpty),

				(ca, canUpdate) -> this.dao.canBeUpdated(entity.getId()).flatMap(BooleanUtil::safeValueOfWithEmpty),

				(ca, canUpdate, canEntityUpdate) -> super.update(entity),

				(ca, canUpdate, canEntityUpdate, updated) -> cacheService.evict(getPolicyCacheName(),
						getCacheKeys(entity.getClientId(), entity.getAppId())).map(evicted -> updated))
				.switchIfEmpty(securityMessageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
						SecurityMessageResourceService.FORBIDDEN_CREATE, getPolicyName()));
	}

	@PreAuthorize("hasAuthority('Authorities.Application_DELETE')")
	@Override
	public Mono<Integer> delete(ULong id) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.read(id),

				(ca, entity) -> this.canUpdatePolicy(ca, entity.getAppId()).flatMap(BooleanUtil::safeValueOfWithEmpty),

				(ca, entity, canDelete) -> this.dao.canBeUpdated(entity.getId())
						.flatMap(BooleanUtil::safeValueOfWithEmpty),

				(ca, entity, canDelete, canEntityDelete) -> super.delete(id),

				(ca, entity, canDelete, canEntityDelete, deleted) -> cacheService.evict(getPolicyCacheName(),
						getCacheKeys(entity.getClientId(), entity.getAppId())).map(evicted -> deleted))
				.switchIfEmpty(securityMessageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
						SecurityMessageResourceService.FORBIDDEN_CREATE, getPolicyName()));
	}

	private Mono<Boolean> canUpdatePolicy(ContextAuthentication ca, ULong appId) {

		ULong loggedInClientId = ULongUtil.valueOf(ca.getLoggedInFromClientId());

		return FlatMapUtil.flatMapMono(

				() -> this.appService.getAppById(appId),

				app -> Mono.zip(
						this.clientService.isBeingManagedBy(loggedInClientId, app.getClientId()),
						this.appService.hasWriteAccess(appId, loggedInClientId),
						(isManaged, canEditApp) -> isManaged || canEditApp),

				(app, managedOrEdit) -> Mono.just(ca.isSystemClient() || managedOrEdit))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractPolicyService.canUpdatePolicy"))
				.switchIfEmpty(Mono.just(Boolean.FALSE));
	}

	@PreAuthorize("hasAuthority('Authorities.Application_CREATE')")
	public Mono<D> create(String clientCode, String appCode, D entity) {

		return FlatMapUtil.flatMapMono(

				() -> this.getClientAndAppId(clientCode, appCode),

				clientAppIds -> this.getClientAppPolicy(clientAppIds.getT1(), clientAppIds.getT2()),

				(clientAppIds, policy) -> {

					if (policy != null && !isDefaultPolicy(policy))
						return securityMessageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
								SecurityMessageResourceService.FORBIDDEN_CREATE, getPolicyName());

					entity.setClientId(clientAppIds.getT1());
					entity.setAppId(clientAppIds.getT2());

					return this.create(entity);
				});
	}

	@PreAuthorize("hasAuthority('Authorities.Application_UPDATE')")
	public Mono<D> update(String clientCode, String appCode, Map<String, Object> fields) {
		return this.getClientAppPolicy(clientCode, appCode)
				.flatMap(policy -> this.isDefaultPolicy(policy) ? Mono.just(policy)
						: this.update(policy.getId(), fields));
	}

	@PreAuthorize("hasAuthority('Authorities.Application_DELETE')")
	public Mono<Integer> delete(String clientCode, String appCode) {
		return this.getClientAppPolicy(clientCode, appCode)
				.flatMap(policy -> this.isDefaultPolicy(policy) ? Mono.just(0)
						: this.delete(policy.getId()));
	}

	public Mono<D> getClientAppPolicy(String clientCode, String appCode) {
		return this.getClientAndAppId(clientCode, appCode)
				.flatMap(clientAppIds -> this.getClientAppPolicy(clientAppIds.getT1(), clientAppIds.getT2()));
	}

	public Mono<D> getClientAppPolicy(ULong clientId, ULong appId) {

		return this.clientHierarchyService.getClientHierarchyIds(clientId)
				.flatMap(
						client -> {
							Integer s = client.intValue();
							return this.getClientAppPolicyInternal(client, appId)
									.filter(Objects::nonNull);
						})
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

	private Mono<Tuple2<ULong, ULong>> getClientAndAppId(String clientCode, String appCode) {
		return FlatMapUtil.flatMapMono(
				() -> clientService.getClientId(clientCode),
				clientId -> appService.getAppByCode(appCode),
				(clientId, app) -> Mono.just(Tuples.of(clientId, app.getId())));
	}

	private boolean isDefaultPolicy(D policy) {
		return policy.getId() == null || policy.getId().equals(DEFAULT_POLICY_ID);
	}

	private String getCacheKeys(ULong clientId, ULong appId) {
		return clientId + ":" + appId;
	}
}
