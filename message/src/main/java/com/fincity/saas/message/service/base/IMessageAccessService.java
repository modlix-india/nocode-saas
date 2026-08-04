package com.fincity.saas.message.service.base;

import org.springframework.http.HttpStatus;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.dto.Client;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.service.MessageResourceService;

import reactor.core.publisher.Mono;

public interface IMessageAccessService {

    MessageResourceService getMsgService();

    IFeignSecurityService getSecurityService();

    default Mono<MessageAccess> hasAccess() {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> Mono.just(ca.isAuthenticated())
                        .flatMap(BooleanUtil::safeValueOfWithEmpty)
                        .switchIfEmpty(this.getMsgService()
                                .throwMessage(
                                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                        MessageResourceService.LOGIN_REQUIRED)),
                (ca, isAuthenticated) -> this.getMessageAccess(ca));
    }

    default Mono<MessageAccess> hasPublicAccess() {
        return FlatMapUtil.flatMapMono(SecurityContextUtil::getUsersContextAuthentication, this::getMessageAccess);
    }

    private Mono<MessageAccess> getMessageAccess(ContextAuthentication ca) {

        if (ca.getUser().getPhoneNumber() == null)
            return this.getMsgService()
                    .throwMessage(
                            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                            MessageResourceService.PHONE_NUMBER_REQUIRED);

        // Both branches validate. This used to short-circuit for authenticated callers and run the
        // checks only when unauthenticated, which is backwards: appCode comes from the URL, so a
        // logged-in user of one app could pass another app's code and operate on its rows, and
        // nothing confirmed their client was entitled to the client whose data they were touching.
        // For the ordinary case where the target is the caller's own client both calls are cheap
        // and trivially true, so correctness here costs a lookup, not a behaviour change.
        if (ca.isAuthenticated())
            return this.validatedAccess(ca, ca.getUrlAppCode(), ca.getClientCode());

        return SecurityContextUtil.resolveAppAndClientCode(null, null)
                .flatMap(acTup -> this.validatedAccess(ca, acTup.getT1(), acTup.getT2()));
    }

    /**
     * Confirms the caller may act on this (app, client) pair at all.
     *
     * <p>Two separate questions, and both matter. {@code appInheritance} asks whether the client is
     * entitled to the app, which stops a caller naming an app they have no business in.
     * {@code isUserClientManageClient} asks whether the caller's own client is that client or
     * manages it, which is what keeps one tenant out of another's settings and lets a managing
     * client legitimately administer the clients beneath it.
     */
    private Mono<MessageAccess> validatedAccess(ContextAuthentication ca, String appCode, String clientCode) {

        return FlatMapUtil.flatMapMono(
                () -> this.getSecurityService()
                        .appInheritance(appCode, ca.getUrlClientCode(), clientCode)
                        .map(clientCodes -> clientCodes.contains(clientCode))
                        .flatMap(BooleanUtil::safeValueOfWithEmpty)
                        .switchIfEmpty(this.getMsgService()
                                .throwMessage(
                                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                        MessageResourceService.FORBIDDEN_APP_ACCESS,
                                        clientCode)),
                hasAppAccess -> this.getSecurityService().getClientByCode(clientCode).map(Client::getId)
                        .flatMap(clientId -> this.getSecurityService()
                                .isUserClientManageClient(appCode, ca.getUser().getId(),
                                        ca.getUser().getClientId(),
                                        clientId))
                        .flatMap(BooleanUtil::safeValueOfWithEmpty)
                        .switchIfEmpty(this.getMsgService()
                                .throwMessage(
                                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                        MessageResourceService.INVALID_USER_FOR_CLIENT,
                                        ca.getUser().getId(),
                                        clientCode)),
                (hasAppAccess, isUserManaged) -> Mono.just(MessageAccess.of(
                        appCode,
                        clientCode,
                        ULongUtil.valueOf(ca.getUser().getId()),
                        hasAppAccess && isUserManaged,
                        ca.getUser())));
    }
}
