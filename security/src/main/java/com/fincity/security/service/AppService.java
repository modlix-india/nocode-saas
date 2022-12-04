package com.fincity.security.service;

import java.util.HashMap;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.kirun.engine.util.string.StringFormatter;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.common.security.jwt.ContextAuthentication;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.security.dao.AppDAO;
import com.fincity.security.dto.App;
import com.fincity.security.jooq.tables.records.SecurityAppRecord;
import com.fincity.security.util.ULongUtil;

import reactor.core.publisher.Mono;

@Service
public class AppService extends AbstractJOOQUpdatableDataService<SecurityAppRecord, ULong, App, AppDAO> {

	private static final String APPLICATION = "Application";

	@Autowired
	private ClientService clientService;

	@Autowired
	private SecurityMessageResourceService messageResourceService;

	@PreAuthorize("hasAuthority('Authorities.APPBUILDER.Application_CREATE')")
	@Override
	public Mono<App> create(App entity) {

		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca ->
				{

			        ULong clientId = ULong.valueOf(ca.getUser()
			                .getClientId());

			        if (entity.getClientId() == null)
				        entity.setClientId(clientId);

			        if (ca.isSystemClient() || entity.getClientId()
			                .equals(clientId))
				        return Mono.just(entity);

			        return this.clientService.isBeingManagedBy(clientId, entity.getClientId())
			                .flatMap(managed -> managed.booleanValue() ? Mono.just(entity) : Mono.empty());
		        },

		        (ca, app) -> super.create(app)

		)
		        .switchIfEmpty(Mono.defer(() -> messageResourceService.throwMessage(HttpStatus.FORBIDDEN,
		                SecurityMessageResourceService.FORBIDDEN_CREATE, APPLICATION)));
	}

	@PreAuthorize("hasAuthority('Authorities.APPBUILDER.Application_UPDATE')")
	@Override
	public Mono<App> update(App entity) {
		return this.read(entity.getClientId())
		        .flatMap(e -> super.update(entity))
		        .switchIfEmpty(Mono.defer(() -> messageResourceService.throwMessage(HttpStatus.NOT_FOUND,
		                SecurityMessageResourceService.OBJECT_NOT_FOUND, APPLICATION, entity.getId())));
	}

	@PreAuthorize("hasAuthority('Authorities.APPBUILDER.Application_UPDATE')")
	@Override
	public Mono<App> update(ULong key, Map<String, Object> fields) {
		return this.read(key)
		        .flatMap(e -> super.update(key, fields))
		        .switchIfEmpty(Mono.defer(() -> messageResourceService.throwMessage(HttpStatus.NOT_FOUND,
		                SecurityMessageResourceService.OBJECT_NOT_FOUND, APPLICATION, key)));
	}

	@PreAuthorize("hasAuthority('Authorities.APPBUILDER.Application_READ')")
	@Override
	public Mono<App> read(ULong id) {
		return super.read(id);
	}

	@PreAuthorize("hasAuthority('Authorities.APPBUILDER.Application_READ')")
	@Override
	public Mono<Page<App>> readPageFilter(Pageable pageable, AbstractCondition condition) {
		return super.readPageFilter(pageable, condition);
	}

	@PreAuthorize("hasAuthority('Authorities.APPBUILDER.Application_DELETE')")
	@Override
	public Mono<Integer> delete(ULong id) {
		return this.read(id)
		        .flatMap(e -> super.delete(id))
		        .switchIfEmpty(Mono.defer(() -> messageResourceService.throwMessage(HttpStatus.NOT_FOUND,
		                SecurityMessageResourceService.OBJECT_NOT_FOUND, APPLICATION, id)));
	}

	@Override
	protected Mono<App> updatableEntity(App entity) {

		return ((AppService) AopContext.currentProxy()).read(entity.getId())
		        .flatMap(existing -> SecurityContextUtil.getUsersContextAuthentication()
		                .flatMap(ca ->
						{

			                existing.setAppName(entity.getAppName());

			                if (ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(ca.getClientTypeCode()))
				                return Mono.just(existing);

			                return clientService.isBeingManagedBy(ULongUtil.valueOf(ca.getUser()
			                        .getClientId()), existing.getClientId())
			                        .flatMap(managed ->
									{
				                        if (managed.booleanValue())
					                        return Mono.just(existing);

				                        return messageResourceService
				                                .getMessage(SecurityMessageResourceService.OBJECT_NOT_FOUND)
				                                .flatMap(msg -> Mono
				                                        .error(() -> new GenericException(HttpStatus.NOT_FOUND,
				                                                StringFormatter.format(msg, APPLICATION, entity.getId()))));
			                        });

		                }));
	}

	@Override
	protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {

		return ((AppService) AopContext.currentProxy()).read(key)
		        .flatMap(existing -> SecurityContextUtil.getUsersContextAuthentication()
		                .flatMap(ca ->
						{

			                Map<String, Object> newMap = new HashMap<>();
			                newMap.put("appName", fields.get("appName"));

			                if (ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(ca.getClientTypeCode()))
				                return Mono.just(newMap);

			                return clientService.isBeingManagedBy(ULongUtil.valueOf(ca.getUser()
			                        .getClientId()), existing.getClientId())
			                        .flatMap(managed ->
									{
				                        if (managed.booleanValue())
					                        return Mono.just(newMap);

				                        return messageResourceService
				                                .getMessage(SecurityMessageResourceService.OBJECT_NOT_FOUND)
				                                .flatMap(msg -> Mono
				                                        .error(() -> new GenericException(HttpStatus.NOT_FOUND,
				                                                StringFormatter.format(msg, APPLICATION, key))));
			                        });

		                }));
	}

	public Mono<Boolean> hasReadAccess(String appCode, String clientCode) {
		return this.dao.hasReadAccess(appCode, clientCode);
	}

	public Mono<Boolean> hasWriteAccess(String appCode, String clientCode) {
		return this.dao.hasWriteAccess(appCode, clientCode);
	}

}
