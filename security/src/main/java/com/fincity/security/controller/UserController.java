package com.fincity.security.controller;

import java.util.List;

import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.security.dao.UserDAO;
import com.fincity.security.dto.RoleV2;
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

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
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

	@PostMapping("/findUserClients")
	public Mono<ResponseEntity<List<UserClient>>> findUserClients(@RequestBody AuthenticationRequest authRequest,
			ServerHttpRequest request) {

		return this.service.findUserClients(authRequest, request)
				.map(ResponseEntity::ok);
	}

	@GetMapping("/makeUserActive")
	public Mono<ResponseEntity<Boolean>> makeUserActive(@RequestParam(required = false) ULong userId) {

		return this.service.makeUserActive(userId)
				.map(ResponseEntity::ok);
	}

	@GetMapping("/makeUserInActive")
	public Mono<ResponseEntity<Boolean>> makeUserInActive(@RequestParam(required = false) ULong userId) {

		return this.service.makeUserInActive(userId)
				.map(ResponseEntity::ok);
	}

	@PostMapping("/unblockUser")
	public Mono<ResponseEntity<Boolean>> unblockUser(@RequestParam(required = false) ULong userId) {

		return this.service.unblockUser(userId)
				.map(ResponseEntity::ok);
	}

	@GetMapping("/availableRoles/{userId}")
	public Mono<ResponseEntity<List<RoleV2>>> getRolesFromUser(@PathVariable ULong userId) {

		return this.userService.getRolesFromGivenUser(userId)
				.map(ResponseEntity::ok);
	}

	@PostMapping("{userId}/updatePassword")
	public Mono<ResponseEntity<Boolean>> updatePassword(@PathVariable ULong userId,
			@RequestBody RequestUpdatePassword passwordRequest) {

		return this.userService.updatePassword(userId, passwordRequest)
				.map(ResponseEntity::ok);
	}

	@PostMapping("updatePassword")
	public Mono<ResponseEntity<Boolean>> updatePassword(@RequestBody RequestUpdatePassword passwordRequest) {

		return this.userService.updatePassword(passwordRequest)
				.map(ResponseEntity::ok);
	}

	@PostMapping("/reset/password/otp/generate")
	public Mono<ResponseEntity<Boolean>> generateOtpResetPassword(@RequestBody AuthenticationRequest authRequest,
			ServerHttpRequest request) {

		return this.userService.generateOtpResetPassword(authRequest, request)
				.map(ResponseEntity::ok);
	}

	@PostMapping("/reset/password/otp/verify")
	public Mono<ResponseEntity<Boolean>> verifyOtpResetPassword(@RequestBody AuthenticationRequest authRequest) {

		return this.userService.verifyOtpResetPassword(authRequest)
				.map(ResponseEntity::ok);
	}

	@PostMapping("/reset/password")
	public Mono<ResponseEntity<Boolean>> resetPassword(@RequestBody RequestUpdatePassword reqPassword) {

		return this.userService.resetPassword(reqPassword)
				.map(ResponseEntity::ok);
	}

}
