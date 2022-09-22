package com.fincity.security.controller;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQDataController;
import com.fincity.security.dao.UserDAO;
import com.fincity.security.dto.User;
import com.fincity.security.jooq.tables.records.SecurityUserRecord;
import com.fincity.security.service.UserService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/security/users")
public class UserController extends AbstractJOOQDataController<SecurityUserRecord, ULong, User, UserDAO, UserService> {

	@Autowired
	private UserService userService;

	@GetMapping("{userId}/removePermission/{permissionId}")
	public Mono<ResponseEntity<Boolean>> removePermission(@PathVariable ULong userId,
	        @PathVariable ULong permissionId) {

		return userService.removePermissionFromUser(userId, permissionId);
	}

	@GetMapping("/{id}/assignPermission/{permissionId}")
	public Mono<ResponseEntity<Boolean>> assignPermission(@PathVariable ULong id, @PathVariable ULong permissionId) {

		return this.userService.assignPermissionToUser(id, permissionId)
		        .map(ResponseEntity::ok);
	}

	@GetMapping("{userId}/removeRole/{roleId}")
	public Mono<ResponseEntity<Boolean>> removeRoleFromUser(@PathVariable ULong userId, @PathVariable ULong roleId) {
		return userService.removeRoleFromUser(userId, roleId)
		        .map(ResponseEntity::ok);
	}
}
