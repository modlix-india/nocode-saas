package com.fincity.saas.commons.core.functions.message;

import org.springframework.http.HttpStatus;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.mongo.function.DefinitionFunction;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.ClientRelationUtil;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

/**
 * Which app and client a message function acts for, and whether the caller may.
 *
 * <p>The message service's internal routes trust whatever app and client they are given, so this is
 * the only check between a server function and another tenant's WhatsApp number or call account.
 *
 * <p>appCode follows {@code AppDataService}: explicit, then the app owning the running function, then
 * the app of the request. Whichever it resolves to, the caller's client must have access to it (it
 * owns the app or has an app access row), unless the caller is the system client. Being the request's
 * app or the running function's owner app is not enough on its own.
 *
 * <p>clientCode defaults to the caller's own client, which is also right inside an event, where core
 * runs as the event's client. An explicit clientCode other than the caller's must be the caller's
 * client's DIRECT managing client (the partner case: a partner's users work the managing client's
 * deals and so send from its numbers), which must not be SYSTEM and must have write access to the
 * resolved app: {@link ClientRelationUtil#isSelfOrDirectManagingClientCode}. A client the caller
 * manages, a grandparent, and SYSTEM (which manages every client) are all refused.
 */
final class MessageTenant {

    private MessageTenant() {}

    static Mono<Tuple2<String, String>> resolve(
            IFeignSecurityService securityService, String appCode, String clientCode) {

        return FlatMapUtil.flatMapMono(
                        SecurityContextUtil::getUsersContextAuthentication,
                        ca -> appCode(securityService, appCode, ca),
                        (ca, app) -> clientCode(securityService, clientCode, ca, app),
                        (ca, app, client) -> Mono.just(Tuples.of(app, client)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MessageTenant.resolve"));
    }

    /**
     * The same app rule, but only the caller's OWN client. For administering a tenant's numbers
     * (linking, pairing, unlinking): a partner may send from its managing client's numbers, but it
     * may not add or remove them.
     */
    static Mono<Tuple2<String, String>> resolveOwn(
            IFeignSecurityService securityService, String appCode, String clientCode) {

        return FlatMapUtil.flatMapMono(
                        SecurityContextUtil::getUsersContextAuthentication,
                        ca -> appCode(securityService, appCode, ca),
                        (ca, app) -> {
                            String own = ca.getClientCode();
                            if (StringUtil.safeIsBlank(own))
                                return Mono.<String>error(
                                        new GenericException(HttpStatus.BAD_REQUEST, "clientCode is required"));
                            if (!StringUtil.safeIsBlank(clientCode) && !clientCode.equals(own))
                                return Mono.<String>error(new GenericException(HttpStatus.FORBIDDEN,
                                        "Only client " + own + " may manage its own WhatsApp numbers, not " + clientCode));
                            return Mono.just(own);
                        },
                        (ca, app, client) -> Mono.just(Tuples.of(app, client)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MessageTenant.resolveOwn"));
    }

    private static Mono<String> appCode(
            IFeignSecurityService securityService, String appCode, ContextAuthentication ca) {

        return Mono.deferContextual(cv -> {
            String implicit = cv.hasKey(DefinitionFunction.OWNER_APP_CODE)
                    ? cv.get(DefinitionFunction.OWNER_APP_CODE)
                    : ca.getUrlAppCode();

            String app = StringUtil.safeIsBlank(appCode) ? implicit : appCode;

            if (StringUtil.safeIsBlank(app))
                return Mono.error(new GenericException(HttpStatus.BAD_REQUEST, "appCode is required"));

            if (ca.isSystemClient())
                return Mono.just(app);

            String own = ca.getClientCode();
            if (StringUtil.safeIsBlank(own))
                return Mono.error(new GenericException(HttpStatus.FORBIDDEN, "Not allowed to message for app " + app));

            return securityService
                    .hasReadAccess(app, own)
                    .defaultIfEmpty(Boolean.FALSE)
                    .flatMap(e -> Boolean.TRUE.equals(e)
                            ? Mono.just(app)
                            : Mono.<String>error(new GenericException(
                                    HttpStatus.FORBIDDEN, "Not allowed to message for app " + app)));
        });
    }

    private static Mono<String> clientCode(
            IFeignSecurityService securityService, String clientCode, ContextAuthentication ca, String app) {

        String own = ca.getClientCode();

        if (StringUtil.safeIsBlank(clientCode) || clientCode.equals(own)) {
            return StringUtil.safeIsBlank(own)
                    ? Mono.error(new GenericException(HttpStatus.BAD_REQUEST, "clientCode is required"))
                    : Mono.just(own);
        }

        return ClientRelationUtil.isSelfOrDirectManagingClientCode(securityService, own, clientCode, app)
                .flatMap(e -> Boolean.TRUE.equals(e)
                        ? Mono.just(clientCode)
                        : Mono.<String>error(new GenericException(
                                HttpStatus.FORBIDDEN, "Not allowed to message on behalf of client " + clientCode)));
    }
}
