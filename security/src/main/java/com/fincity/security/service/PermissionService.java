package com.fincity.security.service;

import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;

import com.fincity.nocode.kirun.engine.util.string.StringFormatter;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.security.dao.PermissionDAO;
import com.fincity.security.dto.Permission;
import com.fincity.security.jooq.tables.records.SecurityPermissionRecord;
import com.fincity.security.jwt.ContextAuthentication;
import com.fincity.security.util.SecurityContextUtil;

import reactor.core.publisher.Mono;

public class PermissionService
        extends AbstractJOOQUpdatableDataService<SecurityPermissionRecord, ULong, Permission, PermissionDAO> {

	@Autowired
	private MessageResourceService messageResourceService;

	@PreAuthorize("hasAuthority('Authorities.Permission_CREATE')")
	@Override
	public Mono<Permission> create(Permission entity) {

		return SecurityContextUtil.getUsersContextAuthentication()
		        .flatMap(ca ->
				{

			        if (!ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(ca.getClientTypeCode())) {
				        return messageResourceService.getMessage(MessageResourceService.FORBIDDEN_CREATE)
				                .flatMap(msg -> Mono.error(() -> new GenericException(HttpStatus.FORBIDDEN,
				                        StringFormatter.format(msg, "Permission"))));
			        }
			        return super.create(entity);
		        });
	}

	@Override
	public Mono<Page<Permission>> readPageFilter(Pageable pageable, AbstractCondition condition) {

		return this.dao.readPageFilter(pageable, condition);
	}
	
	@Override
	protected Mono<Permission> updatableEntity(Permission entity) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected Mono<Map<String, Object>> updatableFields(Map<String, Object> fields) {
		// TODO Auto-generated method stub
		return null;
	}

}
