package com.fincity.saas.message.service.base;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.service.MessageResourceService;
import org.springframework.http.HttpStatus;
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

        if (ca.isAuthenticated()) return Mono.just(MessageAccess.of(ca));

        return FlatMapUtil.flatMapMono(
                () -> SecurityContextUtil.resolveAppAndClientCode(null, null),
                acTup -> this.getSecurityService()
                        .appInheritance(acTup.getT1(), ca.getUrlClientCode(), acTup.getT2())
                        .map(clientCodes -> clientCodes.contains(acTup.getT2()))
                        .flatMap(BooleanUtil::safeValueOfWithEmpty)
                        .switchIfEmpty(this.getMsgService()
                                .throwMessage(
                                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                        MessageResourceService.FORBIDDEN_APP_ACCESS,
                                        acTup.getT2())),
                (acTup, hasAppAccess) -> this.getSecurityService()
                        .isUserBeingManaged(ca.getUser().getId(), acTup.getT2())
                        .flatMap(BooleanUtil::safeValueOfWithEmpty)
                        .switchIfEmpty(this.getMsgService()
                                .throwMessage(
                                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                        MessageResourceService.INVALID_USER_FOR_CLIENT,
                                        ca.getUser().getId(),
                                        acTup.getT2())),
                (acTup, hasAppAccess, isUserManaged) -> Mono.just(MessageAccess.of(
                        acTup.getT1(),
                        acTup.getT2(),
                        ULongUtil.valueOf(ca.getUser().getId()),
                        hasAppAccess && isUserManaged,
                        ca.getUser())));
    }
}
