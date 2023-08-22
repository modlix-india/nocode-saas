package com.fincity.security.controller;

import java.util.List;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.security.dao.UserDAO;
import com.fincity.security.dto.Permission;
import com.fincity.security.dto.Role;
import com.fincity.security.dto.User;
import com.fincity.security.dto.UserClient;
import com.fincity.security.jooq.tables.records.SecurityUserRecord;
import com.fincity.security.model.AuthenticationRequest;
import com.fincity.security.model.RequestUpdatePassword;
import com.fincity.security.service.UserService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/security/users")
public class UserController
		extends AbstractJOOQUpdatableDataController<SecurityUserRecord, ULong, User, UserDAO, UserService> {

	@Autowired
	private UserService userService;

	@GetMapping("{userId}/removePermission/{permissionId}")
	public Mono<ResponseEntity<Boolean>> removePermission(@PathVariable ULong userId,
			@PathVariable ULong permissionId) {

		return this.userService.removePermissionFromUser(userId, permissionId)
				.map(ResponseEntity::ok);
	}

	@GetMapping("/{id}/assignPermission/{permissionId}")
	public Mono<ResponseEntity<Boolean>> assignPermission(@PathVariable ULong id, @PathVariable ULong permissionId) {

		return this.userService.assignPermissionToUser(id, permissionId)
				.map(ResponseEntity::ok);
	}

	@GetMapping("{userId}/removeRole/{roleId}")
	public Mono<ResponseEntity<Boolean>> removeRole(@PathVariable ULong userId, @PathVariable ULong roleId) {

		return userService.removeRoleFromUser(userId, roleId)
				.map(ResponseEntity::ok);
	}

	@GetMapping("{userId}/assignRole/{roleId}")
	public Mono<ResponseEntity<Boolean>> assignRole(@PathVariable ULong userId, @PathVariable ULong roleId) {

		return userService.assignRoleToUser(userId, roleId)
				.map(ResponseEntity::ok);
	}

	@PostMapping("{userId}/updatePassword")
	public Mono<ResponseEntity<Boolean>> updatePasswordForUser(@PathVariable ULong userId,
			@RequestBody RequestUpdatePassword passwordRequest) {

		return SecurityContextUtil.getUsersContextAuthentication()
				.flatMap(ca -> this.userService.updateNewPassword(ca.getUrlAppCode(), ca.getUrlClientCode(), userId,
						passwordRequest, false))
				.map(ResponseEntity::ok);
	}

	@PostMapping("/findUserClients")
	public Mono<ResponseEntity<List<UserClient>>> findUserClients(@RequestBody AuthenticationRequest authRequest,
			ServerHttpRequest request) {

		return this.service.findUserClients(authRequest, request)
				.map(ResponseEntity::ok);
	}

	@GetMapping("/makeUserActive")
	public Mono<ResponseEntity<Boolean>> makeUserActive() {
		return this.service.makeUserActive()
				.map(ResponseEntity::ok);
	}

	@GetMapping("/availablePermissions/{userId}")
	public Mono<ResponseEntity<List<Permission>>> getPermissionsFromUser(@PathVariable ULong userId) {
		return this.userService.getPermissionsFromGivenUser(userId)
				.map(ResponseEntity::ok);
	}

	@GetMapping("/availableRoles/{userId}")
	public Mono<ResponseEntity<List<Role>>> getRolesFromUser(@PathVariable ULong userId) {
		return this.userService.getRolesFromGivenUser(userId)
				.map(ResponseEntity::ok);
	}

	@PostMapping("/resetPassword")
	public Mono<ResponseEntity<Boolean>> changePassword(@RequestBody RequestUpdatePassword passwordRequest) {

		return SecurityContextUtil.getUsersContextAuthentication()
				.flatMap(ca -> this.userService.updateNewPassword(ca.getUrlAppCode(), ca.getUrlClientCode(),
						ULong.valueOf(ca.getUser()
								.getId()),
						passwordRequest, true))
				.map(ResponseEntity::ok);
	}

	@PostMapping("/requestResetPassword")
	public Mono<ResponseEntity<Boolean>> requestResetPassword(@RequestBody AuthenticationRequest authRequest,
			ServerHttpRequest request) {

		return this.userService.resetPasswordRequest(authRequest, request)
				.map(ResponseEntity::ok);
	}
}
