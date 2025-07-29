package com.fincity.security.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.commons.mq.events.EventNames;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
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
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserRequestService
        extends AbstractJOOQUpdatableDataService<SecurityUserRequestRecord, ULong, UserRequest, UserRequestDAO> {

    private final SecurityMessageResourceService msgService;
    private final ClientService clientService;
    private final UserDAO userDao;
    private final AuthenticationService authenticationService;
    private final SoxLogService soxLogService;
    private final ProfileService profileService;
    private final AppService appService;
    private final EventCreationService eventCreationService;

    @Autowired
    public UserRequestService(SecurityMessageResourceService msgService, ClientService clientService,
                              AuthenticationService authenticationService, UserDAO userDao, SoxLogService soxLogService,
                              ProfileService profileService, AppService appService, EventCreationService eventCreationService) {

        this.msgService = msgService;
        this.clientService = clientService;
        this.userDao = userDao;
        this.authenticationService = authenticationService;
        this.soxLogService = soxLogService;
        this.profileService = profileService;
        this.appService = appService;
        this.eventCreationService = eventCreationService;
    }

    @PreAuthorize("hasAuthority('Authorities.Logged_IN')")
    public Mono<Boolean> createRequest(UserAppAccessRequest request) {

        return FlatMapUtil.flatMapMono(

                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> this.clientService.getClientBy(ca.getClientCode()),

                        (ca, client) -> this.appService.getAppByCode(request.getAppCode()),

                        (ca, client, app) -> this.profileService
                                .checkIfUserHasAnyProfile(
                                        ULongUtil.valueOf(ca.getUser().getId()), app.getAppCode())
                                .flatMap(hasAccess -> {
                                    if (Boolean.TRUE.equals(hasAccess)) {
                                        return this.msgService.throwMessage(
                                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                                SecurityMessageResourceService.USER_ALREADY_HAVING_APP_ACCESS, app.getAppCode());
                                    }
                                    return Mono.just(app);
                                }),

                        (ca, client, app, checkedApp) -> this.dao
                                .checkPendingRequestExists(
                                        ULong.valueOf(ca.getUser().getId()), app.getId())
                                .flatMap(exists -> {
                                    if (Boolean.TRUE.equals(exists)) {
                                        return this.msgService.throwMessage(
                                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                                SecurityMessageResourceService.USER_APP_REQUEST_ALREADY_EXISTS, app.getAppCode());
                                    }
                                    return Mono.just(checkedApp);
                                }),

                        (ca, client, app, checkedApp, reqCheck) -> super.create(new UserRequest()
                                .setUserId(ULong.valueOf(ca.getUser().getId()))
                                .setClientId(client.getId())
                                .setAppId(checkedApp.getId())
                                .setRequestId(String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits())))
                                .setStatus(SecurityUserRequestStatus.PENDING)),

                        (ca, client, app, checkedApp, reqCheck, created) ->
                                this.createUserRequestEvent(ca, app, client, created),

                        (ca, client, app, checkedApp, reqCheck, created, adminEvent) ->
                                this.createRequestAcknowledgeEvent(ca, app, client, created))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserRequestService.create"))
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        SecurityMessageResourceService.FORBIDDEN_CREATE,
                        "User Request"));
    }

    @PreAuthorize("hasAuthority('Authorities.User_CREATE')")
    public Mono<Boolean> acceptRequest(UserAppAccessRequest request, ServerHttpRequest requestHttp, ServerHttpResponse response) {

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
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        SecurityMessageResourceService.USER_APP_REQUEST_INCORRECT_STATUS);
                            }
                            return Mono.just(req);
                        }),

                (ca, uReq) -> this.userDao.addProfileToUser(uReq.getUserId(), request.getProfileId()),

                        (ca, uReq, profileAdded) -> super.update(uReq.setStatus(SecurityUserRequestStatus.APPROVED)),

                        (ca, uReq, profileAdded,  updated) -> this.createAcceptRequestEvent(updated, ca))
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
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        SecurityMessageResourceService.USER_APP_REQUEST_INCORRECT_STATUS);
                            }
                            return Mono.just(req);
                        }),

                        (ca, validatedRequest) -> super.update(validatedRequest
                                .setStatus(SecurityUserRequestStatus.REJECTED)),

                        (ca, validatedRequest, updated) -> this.createRejectRequestEvent(updated, ca))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserRequestService.rejectRequest"))
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        SecurityMessageResourceService.FORBIDDEN_UPDATE,
                        "User Request"));
    }

    private Mono<Boolean> createUserRequestEvent(ContextAuthentication ca, App app, Client client, UserRequest userRequest) {

        return FlatMapUtil.flatMapMono(
                () -> this.userDao.readInternal(userRequest.getUserId()),

                user -> this.getAdminEmails(app.getId(), client.getId()),

                (user, tup) -> {

                    Map<String, Object> eventData = Map.of(
                            "requestId", userRequest.getId().toString(),
                            "app", app,
                            "status", userRequest.getStatus().toString(),
                            "adminEmails", tup.getT1(),
                            "addApp", tup.getT2());

                    EventQueObject userRequestEvent = new EventQueObject()
                            .setAppCode(app.getAppCode())
                            .setClientCode(ca.getLoggedInFromClientCode())
                            .setEventName(EventNames.USER_APP_REQUEST)
                            .setData(eventData)
                            .setAuthentication(ca);

                    return this.eventCreationService.createEvent(userRequestEvent).onErrorReturn(Boolean.FALSE);
                });
    }

    private Mono<Boolean> createRequestAcknowledgeEvent(
            ContextAuthentication ca, App app, Client client, UserRequest userRequest) {

        return this.userDao.readInternal(userRequest.getUserId()).flatMap(user -> {

            Map<String, Object> eventData =
                    Map.of("user", user, "requestId", userRequest.getId().toString());

            EventQueObject userRequestEvent = new EventQueObject()
                    .setAppCode(ca.getUrlAppCode())
                    .setClientCode(ca.getLoggedInFromClientCode())
                    .setEventName(EventNames.USER_APP_REQ_ACKNOWLEDGED)
                    .setData(eventData)
                    .setAuthentication(ca);

            return this.eventCreationService.createEvent(userRequestEvent).onErrorReturn(Boolean.FALSE);
        });
    }

    private Mono<Boolean> createAcceptRequestEvent(UserRequest userRequest, ContextAuthentication ca) {

        return this.userDao.readInternal(userRequest.getUserId()).flatMap(user -> {

        Map<String, Object> eventData = Map.of(
                "user", user,
                "requestId", userRequest.getId().toString());

        EventQueObject userRequestEvent = new EventQueObject()
                .setAppCode(ca.getUrlAppCode())
                .setClientCode(ca.getLoggedInFromClientCode())
                .setEventName(EventNames.USER_APP_REQ_APPROVED)
                .setData(eventData)
                .setAuthentication(ca);

        return this.eventCreationService.createEvent(userRequestEvent).onErrorReturn(Boolean.FALSE);
        });
    }

    private Mono<Boolean> createRejectRequestEvent(UserRequest userRequest, ContextAuthentication ca) {

        return this.userDao.readInternal(userRequest.getUserId()).flatMap(user -> {

            Map<String, Object> eventData = Map.of(
                "user", user,
                "requestId", userRequest.getId().toString());

        EventQueObject userRequestEvent = new EventQueObject()
                .setAppCode(ca.getUrlAppCode())
                .setClientCode(ca.getLoggedInFromClientCode())
                .setEventName(EventNames.USER_APP_REQ_REJECTED)
                .setData(eventData)
                .setAuthentication(ca);

        return this.eventCreationService.createEvent(userRequestEvent).onErrorReturn(Boolean.FALSE);

        });
    }

    private Mono<Tuple2<List<String>, Boolean>> getAdminEmails(ULong appId, ULong clientId) {

        List<String> authorities = List.of("Authorities.User_CREATE", "Authorities.ROLE_Owner");

        return this.profileService.getAppProfilesHavingAuthorities(appId, clientId, authorities, Boolean.FALSE)
                .flatMap(adminProfiles -> {
                    if (adminProfiles == null || adminProfiles.isEmpty()) {
                        return this.userDao.getOwners(clientId)
                                .map(users -> users.stream()
                                        .map(User::getEmailId)
                                        .filter(Objects::nonNull)
                                        .collect(Collectors.toList()))
                                .map(emails -> Tuples.of(emails, Boolean.TRUE));
                    }

                    return this.userDao.getUsersForProfiles(adminProfiles)
                            .map(users -> users.stream()
                                    .map(User::getEmailId)
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toList()))
                            .map(emails -> Tuples.of(emails, Boolean.FALSE));
                });
    }

    @Override
    protected Mono<UserRequest> updatableEntity(UserRequest entity) {
        return Mono.just(entity);
    }
}