package com.fincity.security.service;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.UserDAO;
import com.fincity.security.dto.User;
import com.fincity.security.jooq.enums.SecuritySoxLogObjectName;
import com.fincity.security.jooq.tables.records.SecurityUserRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuples;

@Service
public class UserSubOrganizationService
        extends AbstractSecurityUpdatableDataService<SecurityUserRecord, ULong, User, UserDAO> {

    private final SecurityMessageResourceService msgService;

    private final TokenService tokenService;

    private ClientService clientService;

    public UserSubOrganizationService(SecurityMessageResourceService msgService, TokenService tokenService) {
        this.msgService = msgService;
        this.tokenService = tokenService;
    }

    @Lazy
    @Autowired
    private void setClientService(ClientService clientService) {
        this.clientService = clientService;
    }

    private <T> Mono<T> forbiddenError(String message, Object... params) {
        return msgService
                .getMessage(message, params)
                .handle((msg, sink) -> sink.error(new GenericException(HttpStatus.FORBIDDEN, msg)));
    }

    @Override
    protected Mono<ULong> getLoggedInUserId() {
        return SecurityContextUtil.getUsersContextUser().map(ContextUser::getId).map(ULong::valueOf);
    }

    @Override
    public SecuritySoxLogObjectName getSoxObjectName() {
        return SecuritySoxLogObjectName.USER;
    }

    private Mono<Integer> evictTokens(ULong id) {
        return this.tokenService.evictTokensOfUser(id);
    }

    @PreAuthorize("hasAuthority('Authorities.User_UPDATE')")
    public Mono<User> updateReportingManager(ULong userId, ULong managerId) {

        return FlatMapUtil.flatMapMono(
                        SecurityContextUtil::getUsersContextAuthentication,
                        ca -> this.dao.readById(userId),
                        (ca, user) -> ca.isSystemClient()
                                ? Mono.just(Boolean.TRUE)
                                : clientService
                                        .isBeingManagedBy(
                                                ULongUtil.valueOf(ca.getUser().getClientId()), user.getClientId())
                                        .flatMap(BooleanUtil::safeValueOfWithEmpty),
                        (ca, user, sysOrManaged) -> this.canReportTo(user.getClientId(), managerId, userId)
                                .flatMap(canReport -> !BooleanUtil.safeValueOf(canReport)
                                        ? this.msgService.throwMessage(
                                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                                SecurityMessageResourceService.USER_REPORTING_ERROR)
                                        : Mono.just(user)),
                        (ca, user, sysOrManaged, validUser) -> super.update(user.setReportingTo(managerId)),
                        (ca, user, sysOrManaged, validUser, updated) ->
                                this.evictTokens(updated.getId()).map(evicted -> updated))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.updateReportingManager"))
                .switchIfEmpty(
                        this.forbiddenError(SecurityMessageResourceService.FORBIDDEN_UPDATE, "user reporting manager"));
    }

    public Mono<Boolean> canReportTo(ULong clientId, ULong reportingTo, ULong userId) {

        if (reportingTo == null) return Mono.just(Boolean.TRUE);

        return this.dao.canReportTo(clientId, reportingTo, userId);
    }

    public Flux<ULong> getCurrentUserSubOrg() {
        return SecurityContextUtil.getUsersContextAuthentication()
                .map(ca -> ULong.valueOf(ca.getUser().getId()))
                .flatMapMany(this::getSubOrg)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.getCurrentUserSubOrgUserIds"));
    }

    @PreAuthorize("hasAuthority('Authorities.User_READ')")
    public Flux<ULong> getUserSubOrg(ULong userId) {
        return this.getSubOrg(userId)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.getSubOrgUserIdsByUserId"));
    }

    private Flux<ULong> getSubOrg(ULong managerId) {
        return SecurityContextUtil.getUsersContextAuthentication()
                .flatMap(ca -> {
                    ULong clientId = ULong.valueOf(ca.getUser().getClientId());

                    if (managerId.equals(ULong.valueOf(ca.getUser().getId())))
                        return Mono.just(Tuples.of(clientId, managerId));

                    return this.dao.readById(managerId).flatMap(user -> {
                        if (ca.isSystemClient()) return Mono.just(Tuples.of(user.getClientId(), managerId));

                        return this.clientService
                                .isBeingManagedBy(clientId, user.getClientId())
                                .flatMap(isManaged -> {
                                    if (Boolean.TRUE.equals(isManaged))
                                        return Mono.just(Tuples.of(user.getClientId(), managerId));
                                    return this.forbiddenError(
                                            SecurityMessageResourceService.FORBIDDEN_PERMISSION,
                                            "user reporting hierarchy");
                                });
                    });
                })
                .flatMapMany(tuple -> this.dao.getSubOrgUserIds(tuple.getT1(), tuple.getT2(), Boolean.TRUE))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.getAllUsersInSubOrg"));
    }
}
