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
import com.fincity.saas.common.security.jwt.ContextAuthentication;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.security.dao.PermissionDAO;
import com.fincity.security.dto.Permission;
import com.fincity.security.jooq.enums.SecuritySoxLogObjectName;
import com.fincity.security.jooq.tables.records.SecurityPermissionRecord;

import reactor.core.publisher.Mono;

@Service
public class PermissionService
        extends AbstractSecurityUpdatableDataService<SecurityPermissionRecord, ULong, Permission, PermissionDAO> {

	private static final String PERMISSION = "Permission";

	@Autowired
	private SecurityMessageResourceService messageResourceService;

	@Autowired
	private ClientService clientService;

	@Override
	public SecuritySoxLogObjectName getSoxObjectName() {
		return SecuritySoxLogObjectName.PERMISSION;
	}

	@PreAuthorize("hasAuthority('Authorities.Permission_CREATE')")
	@Override
	public Mono<Permission> create(Permission entity) {

		return SecurityContextUtil.getUsersContextAuthentication()
		        .flatMap(ca ->
				{

			        if (!ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(ca.getClientTypeCode())) {
				        return messageResourceService.getMessage(SecurityMessageResourceService.FORBIDDEN_CREATE)
				                .flatMap(msg -> Mono.error(() -> new GenericException(HttpStatus.FORBIDDEN,
				                        StringFormatter.format(msg, PERMISSION))));
			        }
			        return super.create(entity);
		        });
	}

	@PreAuthorize("hasAuthority('Authorities.Permission_READ')")
	@Override
	public Mono<Permission> read(ULong id) {

		return super.read(id).flatMap(p -> SecurityContextUtil.getUsersContextAuthentication()
		        .flatMap(ca ->
				{

			        if (ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(ca.getClientTypeCode()))
				        return Mono.just(p);

			        return clientService.isBeingManagedBy(ULongUtil.valueOf(ca.getUser()
			                .getClientId()), p.getClientId())
			                .flatMap(managed ->
							{
				                if (managed.booleanValue())
					                return Mono.just(p);

				                return messageResourceService
				                        .getMessage(SecurityMessageResourceService.OBJECT_NOT_FOUND)
				                        .flatMap(msg -> Mono.error(() -> new GenericException(HttpStatus.NOT_FOUND,
				                                StringFormatter.format(msg, PERMISSION, id))));
			                });

		        }));
	}

	@PreAuthorize("hasAuthority('Authorities.Permission_READ')")
	@Override
	public Mono<Page<Permission>> readPageFilter(Pageable pageable, AbstractCondition condition) {

		return this.dao.readPageFilter(pageable, condition);
	}

	@Override
	protected Mono<Permission> updatableEntity(Permission entity) {

		return ((PermissionService) AopContext.currentProxy()).read(entity.getId())
		        .flatMap(existing -> SecurityContextUtil.getUsersContextAuthentication()
		                .flatMap(ca ->
						{

			                existing.setDescription(entity.getDescription());
			                existing.setName(entity.getName());

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
				                                .flatMap(msg -> Mono.error(() -> new GenericException(
				                                        HttpStatus.NOT_FOUND,
				                                        StringFormatter.format(msg, PERMISSION, entity.getId()))));
			                        });

		                }));
	}

	@Override
	protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {

		return ((PermissionService) AopContext.currentProxy()).read(key)
		        .flatMap(existing -> SecurityContextUtil.getUsersContextAuthentication()
		                .flatMap(ca ->
						{

			                Map<String, Object> newMap = new HashMap<>();
			                newMap.put("description", fields.get("description"));
			                newMap.put("name", fields.get("name"));

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
				                                                StringFormatter.format(msg, PERMISSION, key))));
			                        });

		                }));
	}

	@PreAuthorize("hasAuthority('Authorities.Permission_UPDATE')")
	@Override
	public Mono<Permission> update(Permission entity) {
		return super.update(entity);
	}

	@PreAuthorize("hasAuthority('Authorities.Permission_UPDATE')")
	@Override
	public Mono<Permission> update(ULong key, Map<String, Object> fields) {
		return super.update(key, fields);
	}

	@PreAuthorize("hasAuthority('Authorities.Permission_DELETE')")
	@Override
	public Mono<Integer> delete(ULong id) {

		return ((PermissionService) AopContext.currentProxy()).read(id)
		        .flatMap(existing -> SecurityContextUtil.getUsersContextAuthentication()
		                .flatMap(ca ->
						{

			                if (ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(ca.getClientTypeCode()))
				                return super.delete(id);

			                return clientService.isBeingManagedBy(ULongUtil.valueOf(ca.getUser()
			                        .getClientId()), existing.getClientId())
			                        .flatMap(managed ->
									{
				                        if (managed.booleanValue())
					                        return super.delete(id);

				                        return messageResourceService
				                                .getMessage(SecurityMessageResourceService.OBJECT_NOT_FOUND)
				                                .flatMap(msg -> Mono
				                                        .error(() -> new GenericException(HttpStatus.NOT_FOUND,
				                                                StringFormatter.format(msg, PERMISSION, id))));
			                        });

		                }));
	}

}
