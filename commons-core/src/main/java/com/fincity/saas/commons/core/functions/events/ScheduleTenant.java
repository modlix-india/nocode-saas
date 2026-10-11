package com.fincity.saas.commons.core.functions.events;

import org.springframework.http.HttpStatus;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.ClientRelationUtil;
import com.fincity.saas.commons.util.StringUtil;

import reactor.core.publisher.Mono;

/**
 * Which client a ScheduleEvent or CancelScheduledEvent call stores its one-off under.
 *
 * <p>A blank clientCode (the default) is the caller's own client. Any other value must pass
 * {@link ClientRelationUtil#isSelfOrDirectManagingClientCode} for the request's app: the caller's
 * client's DIRECT managing client, not SYSTEM, with write access to the app. That is the partner
 * case: a partner's server functions work on the managing client's data, so a reminder they schedule
 * must live where the managing client's users can replace or cancel it under the same key. A client
 * the caller manages, a grandparent, and SYSTEM are all refused.
 */
final class ScheduleTenant {

    private ScheduleTenant() {}

    static Mono<String> clientCode(IFeignSecurityService securityService, ContextAuthentication ca, String clientCode) {

        String own = ca.getClientCode();

        if (StringUtil.safeIsBlank(clientCode) || clientCode.equals(own)) {
            return StringUtil.safeIsBlank(own)
                    ? Mono.error(new GenericException(HttpStatus.BAD_REQUEST, "clientCode is required"))
                    : Mono.just(own);
        }

        if (securityService == null)
            return refused(clientCode);

        return ClientRelationUtil.isSelfOrDirectManagingClientCode(securityService, own, clientCode,
                ca.getUrlAppCode())
                .defaultIfEmpty(Boolean.FALSE)
                .flatMap(ok -> Boolean.TRUE.equals(ok) ? Mono.just(clientCode) : refused(clientCode));
    }

    private static Mono<String> refused(String clientCode) {
        return Mono.error(new GenericException(HttpStatus.FORBIDDEN,
                "Not allowed to schedule events for client " + clientCode));
    }
}
