package com.fincity.security.controller;

import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.security.model.NotificationUser;
import com.fincity.saas.commons.security.model.UsersListRequest;
import com.fincity.saas.commons.util.ConditionUtil;
import com.fincity.security.dao.UserDAO;
import com.fincity.security.dto.Profile;
import com.fincity.security.dto.User;
import com.fincity.security.dto.UserClient;
import com.fincity.security.dto.UserInvite;
import com.fincity.security.dto.UserRequest;
import com.fincity.security.jooq.tables.records.SecurityUserRecord;
import com.fincity.security.model.AuthenticationRequest;
import com.fincity.security.model.RegistrationResponse;
import com.fincity.security.model.RequestUpdatePassword;
import com.fincity.security.model.UserAppAccessRequest;
import com.fincity.security.model.UserRegistrationRequest;
import com.fincity.security.service.UserInviteService;
import com.fincity.security.service.UserRequestService;
import com.fincity.security.service.UserService;
import com.fincity.security.service.UserSubOrganizationService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/security/users")
public class UserController
        extends AbstractJOOQUpdatableDataController<SecurityUserRecord, ULong, User, UserDAO, UserService> {

    private final UserInviteService inviteService;
    private final UserSubOrganizationService userSubOrgService;
    private final UserRequestService requestService;

    public UserController(
            UserInviteService inviteService,
            UserSubOrganizationService userSubOrgService,
            UserRequestService requestService) {
        this.inviteService = inviteService;
        this.userSubOrgService = userSubOrgService;
        this.requestService = requestService;
    }

    @GetMapping("{userId}/removeProfile/{profileId}")
    public Mono<ResponseEntity<Boolean>> removeProfile(@PathVariable ULong userId, @PathVariable ULong profileId) {

        return this.service.removeProfileFromUser(userId, profileId).map(ResponseEntity::ok);
    }

    @GetMapping("{userId}/assignProfile/{profileId}")
    public Mono<ResponseEntity<Boolean>> assignProfile(@PathVariable ULong userId, @PathVariable ULong profileId) {

        return this.service.assignProfileToUser(userId, profileId).map(ResponseEntity::ok);
    }

    @GetMapping("{userId}/app/{appId}/assignedProfiles")
    public Mono<ResponseEntity<List<Profile>>> assignedProfiles(@PathVariable ULong userId, @PathVariable ULong appId) {
        return this.service.assignedProfiles(userId, appId).map(ResponseEntity::ok);
    }

    @GetMapping("{userId}/removeRole/{roleId}")
    public Mono<ResponseEntity<Boolean>> removeRole(@PathVariable ULong userId, @PathVariable ULong roleId) {

        return this.service.removeRoleFromUser(userId, roleId).map(ResponseEntity::ok);
    }

    @GetMapping("{userId}/assignRole/{roleId}")
    public Mono<ResponseEntity<Boolean>> assignRole(@PathVariable ULong userId, @PathVariable ULong roleId) {

        return this.service.assignRoleToUser(userId, roleId).map(ResponseEntity::ok);
    }

    @PostMapping("/findUserClients")
    public Mono<ResponseEntity<List<UserClient>>> findUserClients(
            @RequestBody AuthenticationRequest authRequest,
            @RequestParam(value = "appLevel", required = false, defaultValue = "true") Boolean appLevel,
            ServerHttpRequest request) {

        return this.service.findUserClients(authRequest, appLevel, request).map(ResponseEntity::ok);
    }

    @GetMapping("/makeUserActive")
    public Mono<ResponseEntity<Boolean>> makeUserActive(@RequestParam(required = false) ULong userId) {

        return this.service.makeUserActive(userId).map(ResponseEntity::ok);
    }

    @GetMapping("/makeUserInActive")
    public Mono<ResponseEntity<Boolean>> makeUserInActive(@RequestParam(required = false) ULong userId) {

        return this.service.makeUserInActive(userId).map(ResponseEntity::ok);
    }

    @PostMapping("/unblockUser")
    public Mono<ResponseEntity<Boolean>> unblockUser(@RequestParam(required = false) ULong userId) {

        return this.service.unblockUser(userId).map(ResponseEntity::ok);
    }

    @PostMapping("{userId}/updatePassword")
    public Mono<ResponseEntity<Boolean>> updatePassword(
            @PathVariable ULong userId, @RequestBody RequestUpdatePassword passwordRequest) {

        return this.service.updatePassword(userId, passwordRequest).map(ResponseEntity::ok);
    }

    @PostMapping("updatePassword")
    public Mono<ResponseEntity<Boolean>> updatePassword(@RequestBody RequestUpdatePassword passwordRequest) {

        return this.service.updatePassword(passwordRequest).map(ResponseEntity::ok);
    }

    @PostMapping("/reset/password/otp/generate")
    public Mono<ResponseEntity<Boolean>> generateOtpResetPassword(
            @RequestBody AuthenticationRequest authRequest, ServerHttpRequest request) {

        return this.service.generateOtpResetPassword(authRequest, request).map(ResponseEntity::ok);
    }

    @PostMapping("/reset/password/otp/verify")
    public Mono<ResponseEntity<Boolean>> verifyOtpResetPassword(@RequestBody AuthenticationRequest authRequest) {

        return this.service.verifyOtpResetPassword(authRequest).map(ResponseEntity::ok);
    }

    @PostMapping("/reset/password")
    public Mono<ResponseEntity<Boolean>> resetPassword(@RequestBody RequestUpdatePassword reqPassword) {

        return this.service.resetPassword(reqPassword).map(ResponseEntity::ok);
    }

    @PostMapping("/invite")
    public Mono<ResponseEntity<Map<String, Object>>> inviteUser(@RequestBody UserInvite invite) {
        return this.inviteService.createInvite(invite).map(ResponseEntity::ok);
    }

    @GetMapping("/inviteDetails/{code}")
    public Mono<ResponseEntity<UserInvite>> getInvite(@PathVariable String code) {
        return this.inviteService.getUserInvitation(code).map(ResponseEntity::ok);
    }

    @PostMapping("/acceptInvite")
    public Mono<ResponseEntity<RegistrationResponse>> acceptInvite(
            @RequestBody UserRegistrationRequest userRequest, ServerHttpRequest request, ServerHttpResponse response) {
        return this.inviteService.acceptInvite(userRequest, request, response).map(ResponseEntity::ok);
    }

    @DeleteMapping("/invite/{code}")
    public Mono<ResponseEntity<Boolean>> rejectInvite(@PathVariable String code) {
        return this.inviteService.deleteUserInvitation(code).map(ResponseEntity::ok);
    }

    @GetMapping("/invites")
    public Mono<ResponseEntity<Page<UserInvite>>> getAllInvitedUsers(
            Pageable pageable, @RequestParam(required = false) AbstractCondition condition) {
        return this.inviteService.getAllInvitedUsers(pageable, condition).map(ResponseEntity::ok);
    }

    @GetMapping("/internal" + PATH_ID)
    public Mono<ResponseEntity<User>> getUserInternal(
            @PathVariable ULong id, @RequestParam MultiValueMap<String, String> queryParams) {
        return this.service.readById(id, queryParams).map(ResponseEntity::ok);
    }

    @GetMapping("/internal")
    public Mono<ResponseEntity<List<User>>> getUsersInternal(
            @RequestParam List<ULong> userIds, @RequestParam MultiValueMap<String, String> queryParams) {
        return this.service.readByIds(userIds, queryParams).map(ResponseEntity::ok);
    }

    @GetMapping("/internal/clients")
    public Mono<ResponseEntity<List<User>>> getClientUsersInternal(
            @RequestParam List<ULong> clientIds, @RequestParam MultiValueMap<String, String> queryParams) {
        return this.service.readByClientIds(clientIds, queryParams).map(ResponseEntity::ok);
    }

    @GetMapping("/exists")
    public Mono<ResponseEntity<Boolean>> exists(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phoneNumber) {

        return this.service
                .checkUserExistsAcrossApps(username, email, phoneNumber)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/sub-org")
    public Mono<ResponseEntity<List<ULong>>> getCurrentUserSubOrgUserIds() {
        return this.userSubOrgService.getCurrentUserSubOrg().collectList().map(ResponseEntity::ok);
    }

    @GetMapping("/internal/{userId}/sub-org")
    public Mono<ResponseEntity<List<ULong>>> getUserSubOrgInternal(
            @PathVariable ULong userId, @RequestParam String appCode, @RequestParam ULong clientId) {
        return this.userSubOrgService
                .getUserSubOrgInternal(appCode, clientId, userId)
                .collectList()
                .map(ResponseEntity::ok);
    }

    @PutMapping("/{userId}/reportingManager/{managerId}")
    public Mono<ResponseEntity<User>> updateReportingManager(
            @PathVariable ULong userId, @PathVariable ULong managerId) {
        return this.userSubOrgService.updateManager(userId, managerId).map(ResponseEntity::ok);
    }

    @PutMapping("/{userId}/designation/{designationId}")
    public Mono<ResponseEntity<User>> updateDesignation(@PathVariable ULong userId, @PathVariable ULong designationId) {
        return this.service.updateDesignation(userId, designationId).map(ResponseEntity::ok);
    }

    @PostMapping("/request")
    public Mono<ResponseEntity<UserRequest>> userRequest(@RequestBody UserAppAccessRequest request) {
        return this.requestService.createRequest(request).map(ResponseEntity::ok);
    }

    @PostMapping("/acceptRequest")
    public Mono<ResponseEntity<Boolean>> acceptRequest(
            @RequestBody UserAppAccessRequest userRequest, ServerHttpRequest request, ServerHttpResponse response) {
        return this.requestService.acceptRequest(userRequest, request, response).map(ResponseEntity::ok);
    }

    @PostMapping("/rejectRequest/{requestId}")
    public Mono<ResponseEntity<Boolean>> rejectRequest(@PathVariable String requestId) {
        return this.requestService.rejectRequest(requestId).map(ResponseEntity::ok);
    }

    @GetMapping("/internal/adminEmails")
    public Mono<ResponseEntity<Map<String, Object>>> getUserAdminEmails(ServerHttpRequest request) {
        return this.service.getUserAdminEmails(request).map(ResponseEntity::ok);
    }

    @GetMapping("/requestUser/{requestId}")
    public Mono<ResponseEntity<User>> getUserFromRequestId(@PathVariable String requestId) {
        return this.requestService.getRequestUser(requestId).map(ResponseEntity::ok);
    }

    @PostMapping("/noMapping")
    @Override
    public Mono<ResponseEntity<Page<User>>> readPageFilter(Query query) {
        return Mono.just(ResponseEntity.badRequest().build());
    }

    @GetMapping()
    public Mono<ResponseEntity<Page<User>>> readPageFilter(Pageable pageable, ServerHttpRequest request) {
        pageable = (pageable == null ? PageRequest.of(0, 10, Sort.Direction.ASC, PATH_VARIABLE_ID) : pageable);
        return this.service
                .readPageFilter(pageable, ConditionUtil.parameterMapToMap(request.getQueryParams()))
                .flatMap(page -> this.service
                        .fillDetails(page.getContent(), request.getQueryParams())
                        .thenReturn(page))
                .map(ResponseEntity::ok);
    }

    @PostMapping(PATH_QUERY)
    public Mono<ResponseEntity<Page<User>>> readPageFilter(@RequestBody Query query, ServerHttpRequest request) {

        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(), query.getSort());

        return this.service
                .readPageFilter(pageable, query.getCondition())
                .flatMap(page -> this.service
                        .fillDetails(page.getContent(), request.getQueryParams())
                        .thenReturn(page))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/internal/" + PATH_QUERY)
    public Mono<ResponseEntity<Page<User>>> readPageFilterInternal(
            @RequestBody Query query, @RequestParam MultiValueMap<String, String> queryParams) {

        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(), query.getSort());

        return this.service
                .readPageFilterInternal(pageable, query.getCondition())
                .flatMap(page ->
                        this.service.fillDetails(page.getContent(), queryParams).thenReturn(page))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/internal/notification")
    public Mono<ResponseEntity<List<NotificationUser>>> getUsersForNotification(@RequestBody UsersListRequest request) {
        return this.service.getUsersForNotification(request).map(ResponseEntity::ok);
    }
}
