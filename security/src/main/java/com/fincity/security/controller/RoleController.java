package com.fincity.security.controller;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.security.dao.RoleDAO;
import com.fincity.security.dto.Role;
import com.fincity.security.jooq.tables.records.SecurityRoleRecord;
import com.fincity.security.service.RoleService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/security/roles")
public class RoleController extends AbstractJOOQUpdatableDataController<SecurityRoleRecord, ULong, Role, RoleDAO, RoleService> {

	@Autowired
	private RoleService roleService;

	@GetMapping("{roleId}/assignPermission/{permissionId}")
	public Mono<ResponseEntity<Boolean>> assignPermission(@PathVariable ULong roleId,
	        @PathVariable ULong permissionId) {

		return this.roleService.assignPermissionToRole(roleId, permissionId)
		        .map(ResponseEntity::ok);
	}

	@GetMapping("{roleId}/removesPermission/{permissionId}")
	public Mono<ResponseEntity<Boolean>> removePermission(@PathVariable ULong roleId,
	        @PathVariable ULong permissionId) {

		return this.roleService.assignPermissionToRole(roleId, permissionId)
		        .map(ResponseEntity::ok);
	}

}
