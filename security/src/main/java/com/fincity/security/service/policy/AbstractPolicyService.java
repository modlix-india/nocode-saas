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
import com.fincity.security.service.ClientService;
import com.fincity.security.service.SecurityMessageResourceService;

import reactor.core.publisher.Mono;

@Service
public abstract class AbstractPolicyService<R extends UpdatableRecord<R>, D extends AbstractPolicy, O extends AbstractPolicyDao<R, D>>
		extends AbstractJOOQUpdatableDataService<R, ULong, D, O> {

	@Autowired
	protected SecurityMessageResourceService securityMessageResourceService;

	@Autowired
	@Lazy
	private ClientService clientService;

	@Autowired
	private CacheService cacheService;

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

				(ca, created) -> cacheService.evict(getPolicyCacheName(), created.getClientId(), created.getAppId()),

				(ca, created, evicted) -> Mono.just(created))
				.switchIfEmpty(securityMessageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
						SecurityMessageResourceService.FORBIDDEN_CREATE, getPolicyName()));
	}

	@PreAuthorize("hasAuthority('Authorities.Client_Password_Policy_READ')")
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

	@PreAuthorize("hasAuthority('Authorities.Client_Password_Policy_UPDATE')")
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

	@PreAuthorize("hasAuthority('Authorities.Client_Password_Policy_UPDATE')")
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

	@PreAuthorize("hasAuthority('Authorities.Client_Password_Policy_DELETE')")
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

	public Mono<D> getClientAppPolicy(String clientCode, String appCode) {
		return this.dao.getClientAppPolicy(clientCode, appCode)
				.switchIfEmpty(this.getDefaultPolicy());
	}

	public Mono<D> getClientAppPolicy(ULong clientId, ULong appId) {
		return this.dao.getClientAppPolicy(clientId, appId, clientId)
				.switchIfEmpty(getDefaultPolicy());
	}

	public Mono<String> generatePolicyPassword(ULong clientId, ULong appId) {
		return FlatMapUtil.flatMapMono(
				() -> getClientAppPolicy(clientId, appId),
				policy -> Mono.just(policy.generate()));
	}
}
