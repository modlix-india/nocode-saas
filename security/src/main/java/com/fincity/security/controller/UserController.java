package com.fincity.security.controller;

import java.util.List;

import com.fincity.security.dto.*;
import com.fincity.security.model.RegistrationResponse;
import com.fincity.security.model.UserRegistrationRequest;
import com.fincity.security.service.UserInviteService;
import com.fincity.security.service.UserService;
import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.security.dao.UserDAO;
import com.fincity.security.jooq.tables.records.SecurityUserRecord;
import com.fincity.security.model.AuthenticationRequest;
import com.fincity.security.model.RequestUpdatePassword;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/security/users")
public class UserController
        extends AbstractJOOQUpdatableDataController<SecurityUserRecord, ULong, User, UserDAO, UserService> {

    private final UserInviteService inviteService;

    public UserController(UserInviteService inviteService) {
        this.inviteService = inviteService;
    }

    @GetMapping("{userId}/removeProfile/{profileId}")
    public Mono<ResponseEntity<Boolean>> removeProfile(@PathVariable ULong userId, @PathVariable ULong profileId) {

        return this.service.removeProfileFromUser(userId, profileId)
                .map(ResponseEntity::ok);
    }

    @GetMapping("{userId}/assignProfile/{profileId}")
    public Mono<ResponseEntity<Boolean>> assignProfile(@PathVariable ULong userId, @PathVariable ULong profileId) {

        return this.service.assignProfileToUser(userId, profileId)
                .map(ResponseEntity::ok);
    }

    @GetMapping("{userId}/app/{appId}/assignedProfiles")
    public Mono<ResponseEntity<List<Profile>>> assignedProfiles(@PathVariable ULong userId, @PathVariable ULong appId) {
        return this.service.assignedProfiles(userId, appId).map(ResponseEntity::ok);
    }

    @GetMapping("{userId}/removeRole/{roleId}")
    public Mono<ResponseEntity<Boolean>> removeRole(@PathVariable ULong userId, @PathVariable ULong roleId) {

        return this.service.removeRoleFromUser(userId, roleId)
                .map(ResponseEntity::ok);
    }

    @GetMapping("{userId}/assignRole/{roleId}")
    public Mono<ResponseEntity<Boolean>> assignRole(@PathVariable ULong userId, @PathVariable ULong roleId) {

        return this.service.assignRoleToUser(userId, roleId)
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

    @PostMapping("{userId}/updatePassword")
    public Mono<ResponseEntity<Boolean>> updatePassword(@PathVariable ULong userId,
                                                        @RequestBody RequestUpdatePassword passwordRequest) {

        return this.service.updatePassword(userId, passwordRequest)
                .map(ResponseEntity::ok);
    }

    @PostMapping("updatePassword")
    public Mono<ResponseEntity<Boolean>> updatePassword(@RequestBody RequestUpdatePassword passwordRequest) {

        return this.service.updatePassword(passwordRequest)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/reset/password/otp/generate")
    public Mono<ResponseEntity<Boolean>> generateOtpResetPassword(@RequestBody AuthenticationRequest authRequest,
                                                                  ServerHttpRequest request) {

        return this.service.generateOtpResetPassword(authRequest, request)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/reset/password/otp/verify")
    public Mono<ResponseEntity<Boolean>> verifyOtpResetPassword(@RequestBody AuthenticationRequest authRequest) {

        return this.service.verifyOtpResetPassword(authRequest)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/reset/password")
    public Mono<ResponseEntity<Boolean>> resetPassword(@RequestBody RequestUpdatePassword reqPassword) {

        return this.service.resetPassword(reqPassword)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/internal/getProfileUsers/{appCode}")
    public Mono<ResponseEntity<List<ULong>>> getProfileUsers(@PathVariable String appCode, @RequestBody List<ULong> profileIds) {
        return this.service.getProfileUsers(appCode, profileIds)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/invite")
    public Mono<ResponseEntity<UserInvite>> inviteUser(@RequestBody UserInvite invite) {
        return this.inviteService.create(invite)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/inviteDetails/{code}")
    public Mono<ResponseEntity<UserInvite>> getInvite(@PathVariable String code) {
        return this.inviteService.getUserInvitation(code)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/acceptInvite")
    public Mono<ResponseEntity<RegistrationResponse>> acceptInvite(@RequestBody UserRegistrationRequest userRequest, ServerHttpRequest request, ServerHttpResponse response) {
        return this.inviteService.acceptInvite(userRequest, request, response)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/invite/{code}")
    public Mono<ResponseEntity<Boolean>> rejectInvite(@PathVariable String code) {
        return this.inviteService.deleteUserInvitation(code)
                .map(ResponseEntity::ok);
    }

}
