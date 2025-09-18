package com.fincity.security.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.UserDAO;
import com.fincity.security.dao.UserRequestDAO;
import com.fincity.security.dto.App;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.User;
import com.fincity.security.dto.UserRequest;
import com.fincity.security.jooq.enums.SecurityUserRequestStatus;
import com.fincity.security.jooq.tables.records.SecurityUserRequestRecord;
import com.fincity.security.model.UserAppAccessRequest;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.UUID;

@Service
public class UserRequestService
        extends
        AbstractJOOQUpdatableDataService<SecurityUserRequestRecord, ULong, UserRequest, UserRequestDAO> {

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
                        "User Request"));
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

                ca -> this.dao.readByRequestId(request.getRequestId()).flatMap(req -> {
                    if (req.getStatus() != SecurityUserRequestStatus.PENDING) {
                        return this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST,
                                        msg),
                                SecurityMessageResourceService.USER_APP_REQUEST_INCORRECT_STATUS);
                    }
                    return Mono.just(req);
                }),

                (ca, uReq) -> this.userDao.addProfileToUser(uReq.getUserId(), request.getProfileId()),

                (ca, uReq, profileAdded) -> super.update(
                        uReq.setStatus(SecurityUserRequestStatus.APPROVED))
                        .<Boolean>flatMap(e -> Mono.just(Boolean.TRUE)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserRequestService.acceptRequest"))
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        SecurityMessageResourceService.FORBIDDEN_UPDATE,
                        "User Request"));
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

                ca -> this.dao.readByRequestId(requestId).flatMap(req -> {
                    if (req.getStatus() != SecurityUserRequestStatus.PENDING) {
                        return this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST,
                                        msg),
                                SecurityMessageResourceService.USER_APP_REQUEST_INCORRECT_STATUS);
                    }
                    return Mono.just(req);
                }),

                (ca, validatedRequest) -> super.update(validatedRequest
                        .setStatus(SecurityUserRequestStatus.REJECTED))
                        .<Boolean>flatMap(e -> Mono.just(Boolean.TRUE)))

                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserRequestService.rejectRequest"))
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        SecurityMessageResourceService.FORBIDDEN_UPDATE,
                        "User Request"));
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

                ca -> this.dao.readByRequestId(requestId),

                (ca, req) -> this.clientService
                        .isBeingManagedBy(ULong.valueOf(ca.getUser().getClientId()),
                                req.getClientId())
                        .filter(BooleanUtil::safeValueOf),

                (ca, req, hasAccess) -> this.userDao.readById(req.getUserId()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserRequestService.getRequestUser"));
    }

    @Override
    protected Mono<UserRequest> updatableEntity(UserRequest entity) {
        return Mono.just(entity);
    }
}