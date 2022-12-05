package com.fincity.saas.data.service;

import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.security.service.FeignAuthenticationService;
import com.fincity.saas.data.dao.StorageDAO;
import com.fincity.saas.data.dto.Storage;
import com.fincity.saas.data.jooq.tables.records.DataStorageRecord;

import reactor.core.publisher.Mono;

@Service
public class StorageService extends AbstractJOOQUpdatableDataService<DataStorageRecord, ULong, Storage, StorageDAO> {

	@Autowired
	private FeignAuthenticationService authService;

	@Override
	@PreAuthorize("hasAuthority('Authorities.APPBUILDER.Storage_CREATE')")
	public Mono<Storage> create(Storage entity) {

		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca ->
				{

			        if (entity.getClientCode() == null)
				        entity.setClientCode(ca.getLoggedInFromClientCode());

			        if (ca.isSystemClient())
				        return Mono.just(entity);

			        return this.authService.hasWriteAccess(entity.getAppCode(), entity.getClientCode())
			                .filter(Boolean::booleanValue)
			                .map(e -> entity);
		        },

		        (ca, e) -> super.create(e));
	}

	@Override
	@PreAuthorize("hasAuthority('Authorities.APPBUILDER.Storage_UPDATE')")
	public Mono<Storage> update(Storage entity) {

		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca ->
				{

			        if (entity.getClientCode() == null)
				        entity.setClientCode(ca.getLoggedInFromClientCode());

			        if (ca.isSystemClient())
				        return Mono.just(entity);

			        return this.authService.hasWriteAccess(entity.getAppCode(), entity.getClientCode())
			                .filter(Boolean::booleanValue)
			                .map(e -> entity);
		        },

		        (ca, e) -> super.update(e));
	}

	@Override
	protected Mono<Storage> updatableEntity(Storage entity) {

		return null;
	}

	@Override
	protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {

		return null;
	}

}
