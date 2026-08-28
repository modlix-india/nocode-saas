package com.fincity.security.service;

import java.util.Set;
import java.util.UUID;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.configuration.service.AbstractMessageService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.UserDAO;
import com.fincity.security.dao.UserRequestDAO;
import com.fincity.security.dto.App;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.Profile;
import com.fincity.security.dto.User;
import com.fincity.security.dto.UserRequest;
import com.fincity.security.jooq.enums.SecurityUserRequestStatus;
import com.fincity.security.jooq.tables.records.SecurityUserRequestRecord;
import com.fincity.security.model.UserAppAccessRequest;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class UserRequestService
        extends
        AbstractJOOQUpdatableDataService<SecurityUserRequestRecord, ULong, UserRequest, UserRequestDAO> {

    private static final String USER_REQUEST = "User Request";

    private final SecurityMessageResourceService msgService;
    private final ClientService clientService;
    private final UserDAO userDao;
    private final ProfileService profileService;
    private final AppService appService;

    @Autowired
    public UserRequestService(SecurityMessageResourceService msgService, ClientService clientService,
            UserDAO userDao,
            ProfileService profileService, AppService appService) {

        this.msgService = msgService;
        this.clientService = clientService;
        this.userDao = userDao;
        this.profileService = profileService;
        this.appService = appService;
    }

    @PreAuthorize("hasAuthority('Authorities.Logged_IN')")
    public Mono<UserRequest> createRequest(UserAppAccessRequest request) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.clientService.getClientBy(ca.getClientCode()),

                (ContextAuthentication ca, Client client) -> this.appService.getAppByCode(request.getAppCode()),

                (ca, client, app) -> this.profileService
                        .checkIfUserHasAnyProfile(
                                ULongUtil.valueOf(ca.getUser().getId()),
                                app.getAppCode())
                        .flatMap(hasAccess -> {
                            if (Boolean.TRUE.equals(hasAccess)) {
                                return this.msgService.throwMessage(
                                        msg -> new GenericException(
                                                HttpStatus.BAD_REQUEST,
                                                msg),
                                        SecurityMessageResourceService.USER_ALREADY_HAVING_APP_ACCESS,
                                        app.getAppCode());
                            }
                            return Mono.<App>just(app);
                        }),

                (ContextAuthentication ca, Client client, App app, App checkedApp) -> this.dao
                        .checkPendingRequestExists(
                                ULong.valueOf(ca.getUser().getId()), app.getId())
                        .flatMap(exists -> {
                            if (Boolean.TRUE.equals(exists)) {
                                return this.msgService.throwMessage(
                                        msg -> new GenericException(
                                                HttpStatus.BAD_REQUEST,
                                                msg),
                                        SecurityMessageResourceService.USER_APP_REQUEST_ALREADY_EXISTS,
                                        app.getAppCode());
                            }
                            return Mono.just(checkedApp);
                        }),

                (ca, client, app, checkedApp, reqCheck) -> super.create(new UserRequest()
                        .setUserId(ULong.valueOf(ca.getUser().getId()))
                        .setClientId(client.getId())
                        .setAppId(checkedApp.getId())
                        .setRequestId(String.valueOf(
                                Math.abs(UUID.randomUUID().getMostSignificantBits())))
                        .setStatus(SecurityUserRequestStatus.PENDING)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserRequestService.create"))
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        SecurityMessageResourceService.FORBIDDEN_CREATE,
                        USER_REQUEST));
    }

    /**
     * Paged, filtered list of the access requests the signed-in user may act on.
     * <p>
     * The tenant scoping is not done here - it is enforced in
     * {@code UserRequestDAO.filter(...)}, which ANDs the caller's client
     * hierarchy into the WHERE clause of both the row query and the count query.
     * That keeps the paging honest and means no caller supplied condition can
     * widen the result set.
     */
    @PreAuthorize("hasAuthority('Authorities.User_READ')")
    @Override
    public Mono<Page<UserRequest>> readPageFilter(Pageable pageable, AbstractCondition condition) {
        return super.readPageFilter(pageable, condition)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserRequestService.readPageFilter"));
    }

    @PreAuthorize("hasAuthority('Authorities.User_CREATE')")
    public Mono<Boolean> acceptRequest(UserAppAccessRequest request, ServerHttpRequest requestHttp,
            ServerHttpResponse response) {

        if (request.getRequestId() == null || request.getProfileId() == null) {
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    SecurityMessageResourceService.USER_APP_REQUEST_ACCEPT_INCORRECT_DATA);
        }

        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.readEntitledRequest(ca, request.getRequestId()),

                (ca, uReq) -> this.checkPending(uReq),

                (ca, uReq, pendingReq) -> this.checkProfileAssignable(pendingReq, request.getProfileId()),

                (ca, uReq, pendingReq, profileChecked) -> this.userDao.addProfileToUser(
                        pendingReq.getUserId(), request.getProfileId()),

                (ca, uReq, pendingReq, profileChecked, profileAdded) -> super.update(
                        pendingReq.setStatus(SecurityUserRequestStatus.APPROVED))
                        .<Boolean>map(e -> Boolean.TRUE))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserRequestService.acceptRequest"))
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        SecurityMessageResourceService.FORBIDDEN_UPDATE,
                        USER_REQUEST));
    }

    @PreAuthorize("hasAuthority('Authorities.User_CREATE')")
    public Mono<Boolean> rejectRequest(String requestId) {

        if (requestId == null) {
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    SecurityMessageResourceService.USER_APP_REQUEST_MANDATORY_REQUEST_ID);
        }

        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.readEntitledRequest(ca, requestId),

                (ca, uReq) -> this.checkPending(uReq),

                (ca, uReq, pendingReq) -> super.update(pendingReq
                        .setStatus(SecurityUserRequestStatus.REJECTED))
                        .<Boolean>map(e -> Boolean.TRUE))

                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserRequestService.rejectRequest"))
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        SecurityMessageResourceService.FORBIDDEN_UPDATE,
                        USER_REQUEST));
    }

    @PreAuthorize("hasAuthority('Authorities.User_READ')")
    public Mono<User> getRequestUser(String requestId) {

        if (requestId == null) {
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    SecurityMessageResourceService.USER_APP_REQUEST_MANDATORY_REQUEST_ID);
        }

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.readEntitledRequest(ca, requestId),

                (ca, req) -> this.userDao.readById(req.getUserId()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserRequestService.getRequestUser"));
    }

    /**
     * Resolves a request by its external request id and proves the signed-in user
     * is entitled to act on it. The request id is handed out by the list endpoint
     * and by notification links, so it is an identifier, never a capability -
     * every operation on a request has to come through here.
     */
    private Mono<UserRequest> readEntitledRequest(ContextAuthentication ca, String requestId) {

        return this.dao.readByRequestId(requestId)
                .switchIfEmpty(Mono.defer(() -> this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                        AbstractMessageService.OBJECT_NOT_FOUND,
                        USER_REQUEST, requestId)))
                .flatMap(req -> this.clientService.isUserClientManageClient(ca, req.getClientId())
                        .flatMap(managed -> BooleanUtil.safeValueOf(managed)
                                ? Mono.just(req)
                                : this.msgService.<UserRequest>throwMessage(
                                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                        SecurityMessageResourceService.FORBIDDEN_PERMISSION,
                                        USER_REQUEST)));
    }

    private Mono<UserRequest> checkPending(UserRequest request) {

        if (request.getStatus() != SecurityUserRequestStatus.PENDING)
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    SecurityMessageResourceService.USER_APP_REQUEST_INCORRECT_STATUS);

        return Mono.just(request);
    }

    /**
     * The profile id is caller supplied. It has to belong to the app the request
     * was raised for, and it has to be a profile the requesting user's client is
     * allowed to hold - otherwise accepting a request becomes a way to attach an
     * arbitrary profile to a user.
     */
    private Mono<Boolean> checkProfileAssignable(UserRequest request, ULong profileId) {

        return this.profileService.readInternal(profileId)
                .switchIfEmpty(Mono.defer(() -> this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                        AbstractMessageService.OBJECT_NOT_FOUND,
                        "Profile", profileId)))
                .flatMap((Profile profile) -> {

                    if (profile.getAppId() == null || !profile.getAppId().equals(request.getAppId()))
                        return this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                SecurityMessageResourceService.PROFILE_FORBIDDEN,
                                profileId, request.getUserId());

                    return this.profileService.hasAccessToProfiles(request.getClientId(), Set.of(profileId))
                            .flatMap(hasAccess -> BooleanUtil.safeValueOf(hasAccess)
                                    ? Mono.just(Boolean.TRUE)
                                    : this.msgService.<Boolean>throwMessage(
                                            msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                            SecurityMessageResourceService.PROFILE_FORBIDDEN,
                                            profileId, request.getUserId()));
                });
    }

    @Override
    protected Mono<UserRequest> updatableEntity(UserRequest entity) {
        return Mono.just(entity);
    }
}
