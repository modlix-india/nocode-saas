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
import com.fincity.saas.data.dao.ConnectionDAO;
import com.fincity.saas.data.dto.Connection;
import com.fincity.saas.data.jooq.tables.records.DataConnectionRecord;

import reactor.core.publisher.Mono;

@Service
public class ConnectionService
        extends AbstractJOOQUpdatableDataService<DataConnectionRecord, ULong, Connection, ConnectionDAO> {

	private static final String CONNECTION = "Connection";

	@Autowired
	private FeignAuthenticationService authService;

	@Autowired
	private DataMessageResourceService msgService;

	private static final Set<String> updatableFields = Set.of("dbConnection", "defaultDb");

	@Override
	@PreAuthorize("hasAuthority('Authorities.APPBUILDER.Connection_CREATE')")
	public Mono<Connection> create(Connection entity) {

		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca ->
				{

			        if (entity.getClientCode() == null)
				        entity.setClientCode(ca.getClientCode());

			        if (ca.isSystemClient() || ca.getClientCode()
			                .equals(entity.getClientCode()))
				        return Mono.just(entity);

			        return this.authService.isBeingManaged(ca.getClientCode(), entity.getClientCode())
			                .filter(Boolean::booleanValue)
			                .map(e -> entity);
		        },

		        (ca, e) -> super.create(e),

		        (ca, e, created) -> this.makeDefaultDB(created))
		        .switchIfEmpty(Mono.defer(() -> msgService.throwMessage(HttpStatus.FORBIDDEN,
		                DataMessageResourceService.FORBIDDEN_CREATE, CONNECTION)));
	}

	private Mono<Connection> makeDefaultDB(Connection created) {

		if (!created.isDefaultDb())
			return Mono.just(created);

		return this.dao.makeOtherDBsNotDefault(created.getId(), created.getClientCode())
		        .map(e -> created);
	}

	@Override
	@PreAuthorize("hasAuthority('Authorities.APPBUILDER.Connection_UPDATE')")
	public Mono<Connection> update(Connection entity) {

		return super.update(entity).flatMap(this::makeDefaultDB);
	}

	@Override
	@PreAuthorize("hasAuthority('Authorities.APPBUILDER.Connection_UPDATE')")
	public Mono<Connection> update(ULong key, Map<String, Object> fields) {

		return super.update(key, fields).flatMap(this::makeDefaultDB);
	}

	@Override
	protected Mono<Connection> updatableEntity(Connection entity) {

		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> this.dao.readById(entity.getId()),

		        (ca, connection) ->
				{
			        connection.setDbConnection(entity.getDbConnection());
			        connection.setDefaultDb(entity.isDefaultDb());

			        if (ca.isSystemClient() || ca.getClientCode()
			                .equals(connection.getClientCode()))
				        return Mono.just(connection);

			        return this.authService.isBeingManaged(ca.getClientCode(), connection.getClientCode())
			                .filter(Boolean::booleanValue)
			                .map(e -> connection);
		        })
		        .switchIfEmpty(Mono.defer(() -> msgService.throwMessage(HttpStatus.FORBIDDEN,
		                DataMessageResourceService.OBJECT_NOT_FOUND_TO_UPDATE, CONNECTION, entity.getId())));
	}

	@Override
	protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {

		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> this.dao.readById(key),

		        (ca, connection) ->
				{

			        Map<String, Object> newFields = fields.entrySet()
			                .stream()
			                .filter(e -> updatableFields.contains(e.getKey()))
			                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

			        if (ca.isSystemClient() || (ca.getClientCode()
			                .equals(connection.getClientCode())))
				        return Mono.just(newFields);

			        return this.authService.isBeingManaged(ca.getClientCode(), connection.getClientCode())
			                .filter(Boolean::booleanValue)
			                .map(e -> newFields);
		        })
		        .switchIfEmpty(Mono.defer(() -> msgService.throwMessage(HttpStatus.FORBIDDEN,
		                DataMessageResourceService.OBJECT_NOT_FOUND_TO_UPDATE, CONNECTION, key)));
	}

	@Override
	@PreAuthorize("hasAuthority('Authorities.APPBUILDER.Connection_DELETE')")
	public Mono<Integer> delete(ULong id) {

		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> this.dao.readById(id),

		        (ca, connection) ->
				{

			        if (ca.isSystemClient() || (ca.getClientCode()
			                .equals(connection.getClientCode())))
				        return super.delete(id);

			        return this.authService.isBeingManaged(ca.getClientCode(), connection.getClientCode())
			                .filter(Boolean::booleanValue)
			                .flatMap(e -> super.delete(id));

		        })
		        .switchIfEmpty(Mono.defer(() -> msgService.throwMessage(HttpStatus.FORBIDDEN,
		                DataMessageResourceService.UNABLE_TO_DELETE, CONNECTION, id)));
	}

}
