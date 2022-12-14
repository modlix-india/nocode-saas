package com.fincity.saas.data.service;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.security.service.FeignAuthenticationService;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.data.dao.StorageDAO;
import com.fincity.saas.data.dto.Storage;
import com.fincity.saas.data.jooq.enums.DataStorageStatus;
import com.fincity.saas.data.jooq.tables.records.DataStorageRecord;

import reactor.core.publisher.Mono;

@Service
public class StorageService extends AbstractJOOQUpdatableDataService<DataStorageRecord, ULong, Storage, StorageDAO> {

	private static final String STORAGE = "Storage";

	@Autowired
	private FeignAuthenticationService authService;

	@Autowired
	private DataMessageResourceService msgService;

	private static final Set<String> updatableFields = Set.of("namespace", "name", "isVersioned", "isAudited",
	        "createAuth", "readAuth", "updateAuth", "deleteAuth");

	@Override
	@PreAuthorize("hasAuthority('Authorities.APPBUILDER.Storage_CREATE')")
	public Mono<Storage> create(Storage entity) {

		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> this.checkNameValidations(entity.getName()),

		        (ca, name) ->
				{

			        if (entity.getStatus() == null)
				        entity.setStatus(DataStorageStatus.ACTIVE);

			        if (ca.isSystemClient())
				        return Mono.just(entity);

			        return this.authService.hasWriteAccess(entity.getAppCode(), ca.getClientCode())
			                .filter(Boolean::booleanValue)
			                .map(e -> entity);
		        },

		        (ca, name, e) -> super.create(e),

		        (ca, name, e, created) -> this.applyActivity(created)

		)
		        .switchIfEmpty(Mono.defer(() -> msgService.throwMessage(HttpStatus.FORBIDDEN,
		                DataMessageResourceService.FORBIDDEN_CREATE, STORAGE)));
	}

	private Mono<String> checkNameValidations(String name) {

		if (StringUtil.safeIsBlank(name))
			return msgService.throwMessage(HttpStatus.BAD_REQUEST, DataMessageResourceService.FIELD_MANDATORY, "Name");

		if (name.indexOf('.') != -1)
			return msgService.throwMessage(HttpStatus.BAD_REQUEST, DataMessageResourceService.VALUE_NOT_ALLOWED, name);

		return Mono.just(name);
	}

	@Override
	@PreAuthorize("hasAuthority('Authorities.APPBUILDER.Storage_UPDATE')")
	public Mono<Storage> update(Storage entity) {

		return super.update(entity).flatMap(this::applyActivity);
	}

	@Override
	@PreAuthorize("hasAuthority('Authorities.APPBUILDER.Storage_UPDATE')")
	public Mono<Storage> update(ULong key, Map<String, Object> fields) {

		return super.update(key, fields).flatMap(this::applyActivity);
	}

	@Override
	protected Mono<Storage> updatableEntity(Storage entity) {

		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> this.checkNameValidations(entity.getName()),

		        (ca, name) -> this.dao.readById(entity.getId()),

		        (ca, name, storage) ->
				{

			        if (storage.getStatus() != DataStorageStatus.ACTIVE)
				        return msgService.throwMessage(HttpStatus.FORBIDDEN,
				                DataMessageResourceService.STORAGE_NOT_ACTIVE,
				                storage.getNamespace() == null ? storage.getName()
				                        : storage.getNamespace() + "." + storage.getName());

			        storage.setCreateAuth(entity.getCreateAuth());
			        storage.setDeleteAuth(entity.getDeleteAuth());
			        storage.setIsAudited(entity.getIsAudited());
			        storage.setIsVersioned(entity.getIsVersioned());
			        storage.setName(entity.getName());
			        storage.setNamespace(entity.getNamespace());
			        storage.setUpdateAuth(entity.getUpdateAuth());
			        storage.setReadAuth(entity.getReadAuth());

			        if (ca.isSystemClient())
				        return Mono.just(storage);

			        return this.authService.hasWriteAccess(storage.getAppCode(), ca.getClientCode())
			                .filter(Boolean::booleanValue)
			                .map(e -> storage);
		        })
		        .switchIfEmpty(Mono.defer(() -> msgService.throwMessage(HttpStatus.FORBIDDEN,
		                DataMessageResourceService.OBJECT_NOT_FOUND_TO_UPDATE, STORAGE, entity.getId())));
	}

	@Override
	protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {

		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> this.dao.readById(key),

		        (ca, storage) ->
				{

			        Map<String, Object> newFields = fields.entrySet()
			                .stream()
			                .filter(e -> updatableFields.contains(e.getKey()))
			                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

			        if (ca.isSystemClient())
				        return Mono.just(newFields);

			        return this.authService.hasWriteAccess(storage.getAppCode(), ca.getClientCode())
			                .filter(Boolean::booleanValue)
			                .map(e -> newFields);
		        })
		        .switchIfEmpty(Mono.defer(() -> msgService.throwMessage(HttpStatus.FORBIDDEN,
		                DataMessageResourceService.OBJECT_NOT_FOUND_TO_UPDATE, STORAGE, key)));
	}

	@Override
	@PreAuthorize("hasAuthority('Authorities.APPBUILDER.Storage_DELETE')")
	public Mono<Integer> delete(ULong id) {

		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> this.dao.readById(id),

		        (ca, storage) ->
				{

			        if (ca.isSystemClient())
				        return super.delete(id);

			        return this.authService.hasWriteAccess(storage.getAppCode(), ca.getClientCode())
			                .filter(Boolean::booleanValue)
			                .flatMap(e -> super.delete(id));

		        },

		        (ca, storage, rows) -> this.applyActivity(storage)
		                .map(e -> rows))
		        .switchIfEmpty(Mono.defer(() -> msgService.throwMessage(HttpStatus.FORBIDDEN,
		                DataMessageResourceService.UNABLE_TO_DELETE, STORAGE, id)));
	}

	private Mono<Storage> applyActivity(Storage storage) {
		return Mono.just(storage);
	}
}
