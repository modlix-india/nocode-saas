package com.fincity.security.service;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.service.AbstractJOOQDataService;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.security.dao.ClientHierarchyDAO;
import com.fincity.security.dto.ClientHierarchy;
import com.fincity.security.jooq.tables.records.SecurityClientHierarchyRecord;

import reactor.core.publisher.Mono;

@Service
public class ClientHierarchyService
		extends AbstractJOOQDataService<SecurityClientHierarchyRecord, ULong, ClientHierarchy, ClientHierarchyDAO> {

	@Autowired
	@Lazy
	private ClientService clientService;

	@Autowired
	private CacheService cacheService;

	private static final String CLIENT_HIERARCHY_CACHE_NAME = "clientHierarchy";

	private static final String USER_CLIENT_HIERARCHY_CACHE_NAME = "userClientHierarchy";

	public Mono<ClientHierarchy> create(ULong managingClientId, ULong clientId) {

		return FlatMapUtil.flatMapMono(

				() -> {
					if (clientId.equals(managingClientId))
						return Mono.empty();

					return Mono.just(Boolean.TRUE);
				},
				areSame -> this.getClientHierarchy(managingClientId),
				(areSame, manageClientHie) -> {

					if (!manageClientHie.canAddLevel())
						return Mono.empty();

					ClientHierarchy clientHierarchy = new ClientHierarchy()
							.setClientId(clientId)
							.setManageClientLevel0(managingClientId)
							.setManageClientLevel1(manageClientHie.getManageClientLevel0())
							.setManageClientLevel2(manageClientHie.getManageClientLevel1() != null
									? manageClientHie.getManageClientLevel2()
									: null)
							.setManageClientLevel3(manageClientHie.getManageClientLevel2() != null
									? manageClientHie.getManageClientLevel3()
									: null);

					return this.create(clientHierarchy);
				});
	}

	public Mono<ClientHierarchy> getClientHierarchy(ULong clientId) {
		return this.cacheService.cacheValueOrGet(CLIENT_HIERARCHY_CACHE_NAME,
				() -> this.dao.getClientHierarchy(clientId), clientId);
	}

	public Mono<ClientHierarchy> getUserClientHierarchy(ULong userId) {
		return this.cacheService.cacheValueOrGet(USER_CLIENT_HIERARCHY_CACHE_NAME,
				() -> this.dao.getUserClientHierarchy(userId), userId);
	}

	public Mono<ClientHierarchy> getClientHierarchy(String clientCode) {
		return this.clientService.getClientId(clientCode).flatMap(this::getClientHierarchy);
	}

	public Mono<Boolean> isBeingManagedBy(ULong managingClientId, ULong clientId) {

		if (managingClientId.equals(clientId))
			return Mono.just(Boolean.TRUE);

		return this.getClientHierarchy(managingClientId)
				.flatMap(clientHierarchy -> Mono.just(clientHierarchy.isManagedBy(clientId)))
				.switchIfEmpty(Mono.just(Boolean.FALSE));
	}

	public Mono<Boolean> isBeingManagedBy(String managingClientCode, String clientCode) {

		if (managingClientCode.equals(clientCode))
			return Mono.just(Boolean.TRUE);

		return FlatMapUtil.flatMapMono(

				() -> this.clientService.getClientId(clientCode),

				clientId -> this.getClientHierarchy(managingClientCode),

				(clientId, clientHierarchy) -> Mono.just(clientHierarchy.isManagedBy(clientId)))
				.switchIfEmpty(Mono.just(Boolean.FALSE));
	}

	public Mono<ULong> getManagingClient(ULong clientId, ClientHierarchy.Level level) {
		return this.getClientHierarchy(clientId).map(clientHierarchy -> clientHierarchy.getManagingClient(level));
	}

	public Mono<Boolean> isUserBeingManaged(ULong managingClientId, ULong userId) {
		return this.getUserClientHierarchy(userId)
				.flatMap(clientHierarchy -> Mono.just(clientHierarchy.isManagedBy(managingClientId)))
				.switchIfEmpty(Mono.just(Boolean.FALSE));
	}

	public Mono<Boolean> isUserBeingManaged(String managingClientCode, ULong userId) {

		return FlatMapUtil.flatMapMono(

				() -> this.clientService.getClientId(managingClientCode),

				managingClientId -> isUserBeingManaged(managingClientId, userId))
				.switchIfEmpty(Mono.just(Boolean.FALSE));
	}
}
