package com.fincity.security.service;

import static com.fincity.saas.commons.util.StringUtil.safeIsBlank;
import static com.fincity.security.jooq.enums.SecuritySoxLogActionName.CREATE;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQDataService;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.UserDAO;
import com.fincity.security.dao.UserInviteDAO;
import com.fincity.security.dto.User;
import com.fincity.security.dto.UserInvite;
import com.fincity.security.jooq.enums.SecuritySoxLogObjectName;
import com.fincity.security.jooq.enums.SecurityUserStatusCode;
import com.fincity.security.jooq.tables.records.SecurityUserInviteRecord;
import com.fincity.security.model.AuthenticationRequest;
import com.fincity.security.model.AuthenticationResponse;
import com.fincity.security.model.RegistrationResponse;
import com.fincity.security.model.UserRegistrationRequest;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class UserInviteService
        extends AbstractJOOQDataService<SecurityUserInviteRecord, ULong, UserInvite, UserInviteDAO> {

    private final SecurityMessageResourceService msgService;
    private final ClientService clientService;
    private final UserDAO userDao;
    private final AuthenticationService authenticationService;
    private final SoxLogService soxLogService;
    private final ProfileService profileService;

    public UserInviteService(SecurityMessageResourceService msgService, ClientService clientService,
            AuthenticationService authenticationService, UserDAO userDao, SoxLogService soxLogService,
            ProfileService profileService) {

        this.msgService = msgService;
        this.clientService = clientService;
        this.userDao = userDao;
        this.authenticationService = authenticationService;
        this.soxLogService = soxLogService;
        this.profileService = profileService;
    }

    @PreAuthorize("hasAuthority('Authorities.User_CREATE')")
    public Mono<Map<String, Object>> createInvite(UserInvite entity) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,
                ca -> {
                    if (entity.getClientId() == null) {
                        entity.setClientId(ULong.valueOf(ca.getUser().getClientId()));
                        return Mono.just(entity);
                    }

                    return this.clientService
                            .isBeingManagedBy(ULong.valueOf(ca.getUser().getClientId()),
                                    entity.getClientId())
                            .filter(BooleanUtil::safeValueOf)
                            .map(x -> entity);
                },

                (ca, invite) -> {

                    if (entity.getReportingTo() == null)
                        return Mono.just(Boolean.TRUE);

                    return this.userDao.readById(entity.getReportingTo())
                            .filter(user -> user.getClientId().equals(entity.getClientId()))
                            .map(x -> Boolean.TRUE);
                },
                (ca, invite, reportingToInSameClient) -> invite.getProfileId() == null
                        ? Mono.just(true)
                        : this.profileService
                                .hasAccessToProfiles(
                                        ULong.valueOf(ca.getUser()
                                                .getClientId()),
                                        Set.of(invite.getProfileId()))
                                .filter(BooleanUtil::safeValueOf),

                (ca, invite, reportingToInSameClient, hasAccess) -> this.userDao
                        .checkUserExistsForInvite(
                                entity.getClientId(),
                                entity.getUserName(),
                                entity.getEmailId(),
                                entity.getPhoneNumber())
                        .flatMap(exists -> {
                            if (exists)
                                return this.addUserProfile(entity);

                            invite.setInviteCode(
                                    UUID.randomUUID().toString().replace("-", ""));
                            return super.create(invite).flatMap(createdInvite -> {
                                Map<String, Object> result = new HashMap<>();
                                result.put("userRequest", createdInvite);
                                result.put("existingUser", Boolean.FALSE);
                                return Mono.just(result);
                            });
                        }))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserInviteService.create"))
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        SecurityMessageResourceService.FORBIDDEN_CREATE,
                        "User Invite"));
    }

    public Mono<UserInvite> getUserInvitation(String code) {
        return this.dao.getUserInvitation(code);
    }

    public Mono<Boolean> deleteUserInvitation(String code) {
        return this.dao.deleteUserInvitation(code);
    }

    public Mono<RegistrationResponse> acceptInvite(UserRegistrationRequest userRequest, ServerHttpRequest request,
            ServerHttpResponse response) {
        return FlatMapUtil.flatMapMono(

                () -> this.dao.getUserInvitation(userRequest.getInviteCode()),

                userInvite -> this.createWithInvitation(userRequest, userInvite, request, response))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserInviteService.acceptInvite"))
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        SecurityMessageResourceService.FORBIDDEN_CREATE,
                        "User Invitation Error"));
    }

    public Mono<RegistrationResponse> createWithInvitation(UserRegistrationRequest request, UserInvite userInvite,
            ServerHttpRequest httpRequest, ServerHttpResponse response) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.createWithInvitationInternal(request, userInvite),

                (ca, createdUser) -> this
                        .getClientAuthenticationResponse(request, createdUser.getId(),
                                request.getInputPass(), httpRequest, response)
                        .<RegistrationResponse>map(authResp -> new RegistrationResponse()
                                .setUserId(createdUser.getId())
                                .setCreated(true)
                                .setAuthentication(authResp)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserInviteService.createWithInvitation"))
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg), "User"));
    }

    private Mono<AuthenticationResponse> getClientAuthenticationResponse(
            UserRegistrationRequest registrationRequest,
            ULong userId, String password, ServerHttpRequest request, ServerHttpResponse response) {

        AuthenticationRequest authRequest = new AuthenticationRequest().setUserId(userId);

        if (registrationRequest.getInputPassType() != null)
            return switch (registrationRequest.getInputPassType()) {
                case PASSWORD ->
                    this.authenticationService.authenticate(authRequest.setPassword(password),
                            request, response);
                case PIN -> this.authenticationService.authenticate(authRequest.setPin(password),
                        request, response);
                case OTP -> Mono.empty();
            };

        if (!safeIsBlank(registrationRequest.getSocialRegisterState()))
            return this.authenticationService.authenticateWSocial(
                    authRequest.setSocialRegisterState(
                            registrationRequest.getSocialRegisterState()),
                    request,
                    response);

        return Mono.empty();
    }

    private Mono<User> createWithInvitationInternal(UserRegistrationRequest request, UserInvite userInvite) {

        User user = request.getUser();

        String password = request.getInputPass(request.getPassType());
        user.setPassword(null);
        user.setPasswordHashed(false);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);
        user.setNoFailedAttempt((short) 0);
        user.setNoPinFailedAttempt((short) 0);
        user.setNoOtpFailedAttempt((short) 0);
        user.setStatusCode(SecurityUserStatusCode.ACTIVE);

        if (user.getFirstName() == null)
            user.setFirstName(userInvite.getFirstName());
        if (user.getLastName() == null)
            user.setLastName(userInvite.getLastName());

        if (!safeIsBlank(userInvite.getPhoneNumber()))
            user.setPhoneNumber(userInvite.getPhoneNumber());
        if (!safeIsBlank(userInvite.getEmailId()))
            user.setEmailId(userInvite.getEmailId());
        if (!safeIsBlank(userInvite.getUserName()))
            user.setUserName(userInvite.getUserName());

        user.setClientId(userInvite.getClientId());
        user.setDesignationId(userInvite.getDesignationId());
        user.setReportingTo(userInvite.getReportingTo());

        return FlatMapUtil.flatMapMono(
                () ->

                this.userDao.checkUserExists(user.getClientId(), user.getUserName(), user.getEmailId(),
                        user.getPhoneNumber(), null)
                        .filter(userExists -> !userExists).map(userExists -> Boolean.FALSE),

                userExists -> this.userDao.create(user),

                (userExists, createdUser) -> {
                    this.soxLogService.createLog(createdUser.getId(), CREATE,
                            SecuritySoxLogObjectName.USER, "User created");

                    return this.userDao
                            .setPassword(createdUser.getId(), createdUser.getId(), password,
                                    request.getPassType())
                            .map(result -> result > 0)
                            .flatMap(BooleanUtil::safeValueOfWithEmpty);
                },

                (userExists, createdUser, passSet) -> (userInvite.getProfileId() != null)
                        ? profileService.hasAccessToProfiles(user.getClientId(),
                                Set.of(userInvite.getProfileId()))
                        : Mono.just(Boolean.FALSE),

                (userExists, createdUser, passSet, hasAddableProfile) -> {
                    if (!BooleanUtil.safeValueOf(hasAddableProfile))
                        return Mono.just(createdUser);

                    return this.userDao
                            .addProfileToUser(createdUser.getId(),
                                    userInvite.getProfileId())
                            .map(e -> createdUser);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                        "UserInviteService.createWithInvitationInternal"))
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg), "User"));
    }

    private Mono<Map<String, Object>> addUserProfile(UserInvite invite) {

        if (invite.getProfileId() == null)
            return Mono.empty();

        return FlatMapUtil.flatMapMono(

                () -> this.userDao.getUserForInvite(
                        invite.getClientId(), invite.getUserName(), invite.getEmailId(),
                        invite.getPhoneNumber()),

                user -> this.profileService
                        .hasAccessToProfiles(user.getClientId(), Set.of(invite.getProfileId()))
                        .filter(BooleanUtil::safeValueOf),

                (user, hasAccessToProfiles) -> this.userDao
                        .addProfileToUser(user.getId(), invite.getProfileId())
                        .flatMap(e -> Mono.just(Map.of("userRequest", invite, "existingUser",
                                Boolean.TRUE))));
    }

    public Mono<Page<UserInvite>> getAllInvitedUsers(Pageable pageable, AbstractCondition condition) {
        return this.readPageFilter(pageable, condition);
    }

}
