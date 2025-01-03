package com.fincity.security.service.policy;

import java.util.HashMap;
import java.util.Map;

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
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.security.dao.policy.AbstractPolicyDao;
import com.fincity.security.dto.policy.AbstractPolicy;
import com.fincity.security.service.AppService;
import com.fincity.security.service.ClientService;
import com.fincity.security.service.SecurityMessageResourceService;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
public abstract class AbstractPolicyService<R extends UpdatableRecord<R>, D extends AbstractPolicy, O extends AbstractPolicyDao<R, D>>
		extends AbstractJOOQUpdatableDataService<R, ULong, D, O> {

	@Autowired
	protected SecurityMessageResourceService securityMessageResourceService;

	@Autowired
	@Lazy
	private ClientService clientService;

	@Autowired
	@Lazy
	private AppService appService;

	@Autowired
	private CacheService cacheService;

	protected static final ULong DEFAULT_POLICY_ID = ULong.MIN;

	protected abstract String getPolicyName();

	public abstract String getPolicyCacheName();

	protected abstract Mono<D> getDefaultPolicy();

	@PreAuthorize("hasAuthority('Authorities.Client_Password_Policy_CREATE')")
	@Override
	public Mono<D> create(D entity) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> {
					ULong currentUser = ULong.valueOf(ca.getLoggedInFromClientId());

					if (ca.isSystemClient() || currentUser.equals(entity.getClientId()))
						return super.create(entity);

					return this.clientService.isBeingManagedBy(currentUser, entity.getClientId())
							.flatMap(managed -> Boolean.TRUE.equals(managed) ? super.create(entity) : Mono.empty());
				},

				(ca, created) -> cacheService.evict(getPolicyCacheName(), created.getClientId(),
						created.getAppId()),

				(ca, created, evicted) -> Mono.just(created))
				.switchIfEmpty(securityMessageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
						SecurityMessageResourceService.FORBIDDEN_CREATE, getPolicyName()));
	}

	@PreAuthorize("hasAuthority('Authorities.Client_READ')")
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
		fields.remove("updatedAt");
		fields.remove("updatedBy");
		fields.remove("createdAt");
		fields.remove("createdBy");

		return Mono.just(fields);
	}

	@PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
	@Override
	public Mono<D> update(ULong key, Map<String, Object> fields) {

		return FlatMapUtil.flatMapMono(

				() -> this.dao.canBeUpdated(key),

				canUpdate -> Boolean.TRUE.equals(canUpdate) ? super.update(key, fields) : Mono.empty(),

				(canUpdate, updated) -> cacheService.evict(getPolicyCacheName(), updated.getClientId(),
						updated.getAppId()),

				(canUpdate, updated, evicted) -> Mono.just(updated))
				.switchIfEmpty(securityMessageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
						SecurityMessageResourceService.FORBIDDEN_CREATE, getPolicyName()));
	}

	@PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
	@Override
	public Mono<D> update(D entity) {

		return FlatMapUtil.flatMapMono(

				() -> this.dao.canBeUpdated(entity.getId()),

				canUpdate -> Boolean.TRUE.equals(canUpdate) ? super.update(entity) : Mono.empty(),

				(canUpdate, updated) -> cacheService.evict(getPolicyCacheName(), updated.getClientId(),
						updated.getAppId()),

				(canUpdate, updated, evicted) -> Mono.just(updated))
				.switchIfEmpty(securityMessageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
						SecurityMessageResourceService.FORBIDDEN_CREATE, getPolicyName()));
	}

	@PreAuthorize("hasAuthority('Authorities.Client_DELETE')")
	@Override
	public Mono<Integer> delete(ULong id) {

		return FlatMapUtil.flatMapMono(

				() -> this.read(id),

				entity -> this.dao.canBeUpdated(entity.getId()),

				(entity, canDelete) -> Boolean.TRUE.equals(canDelete) ? super.delete(id) : Mono.empty(),

				(entity, canDelete, deleted) -> cacheService.evict(getPolicyCacheName(), entity.getClientId(),
						entity.getAppId()),

				(entity, canDelete, deleted, evicted) -> Mono.just(deleted))
				.switchIfEmpty(securityMessageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
						SecurityMessageResourceService.FORBIDDEN_CREATE, getPolicyName()));
	}

	@PreAuthorize("hasAuthority('Authorities.Client_CREATE')")
	public Mono<D> create(String clientCode, String appCode, D entity) {

		return FlatMapUtil.flatMapMono(
				() -> this.getClientAndAppId(clientCode, appCode),

				clientAppIds -> this.read(clientAppIds.getT1(), clientAppIds.getT2()),

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

	@PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
	public Mono<D> update(String clientCode, String appCode, Map<String, Object> fields) {

		return FlatMapUtil.flatMapMono(
				() -> this.read(clientCode, appCode),
				policy -> {

					if (isDefaultPolicy(policy))
						return Mono.just(policy);

					return this.update(policy.getId(), fields);
				});
	}

	@PreAuthorize("hasAuthority('Authorities.Client_DELETE')")
	public Mono<Integer> delete(String clientCode, String appCode) {

		return FlatMapUtil.flatMapMono(
				() -> this.read(clientCode, appCode),
				policy -> {

					if (isDefaultPolicy(policy))
						return Mono.just(0);

					return this.delete(policy.getId());
				});
	}

	public Mono<D> read(String clientCode, String appCode) {

		return FlatMapUtil.flatMapMono(
				() -> this.getClientAndAppId(clientCode, appCode),
				clientAppIds -> this.read(clientAppIds.getT1(), clientAppIds.getT2()));
	}

	public Mono<D> read(ULong clientId, ULong appId) {

		return this.cacheService.cacheEmptyValueOrGet(this.getPolicyCacheName(),
				() -> this.dao.getClientAppPolicy(clientId, appId, clientId),
				clientId, appId)
				.switchIfEmpty(this.getDefaultPolicy());
	}

	public Mono<String> generatePolicyPassword(ULong clientId, ULong appId) {

		return FlatMapUtil.flatMapMono(
				() -> this.read(clientId, appId),
				policy -> Mono.just(policy.generate()));
	}

	private Mono<Tuple2<ULong, ULong>> getClientAndAppId(String clientCode, String appCode) {

		return FlatMapUtil.flatMapMono(
				() -> clientService.getClientBy(clientCode),
				client -> appService.getAppByCode(appCode),
				(client, app) -> Mono.just(Tuples.of(client.getId(), app.getId())));
	}

	private boolean isDefaultPolicy(D policy) {
		return policy.getId() == null || policy.getId().equals(DEFAULT_POLICY_ID);
	}
}
