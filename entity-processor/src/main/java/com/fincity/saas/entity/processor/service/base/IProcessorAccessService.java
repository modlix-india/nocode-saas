package com.fincity.saas.entity.processor.service.base;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.service.IAuthenticationService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

public interface IProcessorAccessService {

    ProcessorMessageResourceService getMsgService();

    IFeignSecurityService getSecurityService();

    CacheService getCacheService();

    default Mono<ProcessorAccess> hasAccess() {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> Mono.just(ca.isAuthenticated())
                        .flatMap(BooleanUtil::safeValueOfWithEmpty)
                        .switchIfEmpty(this.getMsgService()
                                .throwMessage(
                                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                        ProcessorMessageResourceService.LOGIN_REQUIRED)),
                (ca, isAuthenticated) -> this.getCachedProcessorAccess(ca));
    }

    default Mono<ProcessorAccess> hasPublicAccess() {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication, this::getCachedProcessorAccess);
    }

    private Mono<ProcessorAccess> getCachedProcessorAccess(ContextAuthentication ca) {
        if (ca.getAccessToken() == null) return this.getProcessorAccess(ca);

        return this.getCacheService()
                .cacheValueOrGet(
                        IAuthenticationService.CACHE_NAME_TOKEN_ENTITY_PROCESSOR,
                        () -> this.getProcessorAccess(ca),
                        ca.getAccessToken());
    }

    private Mono<ProcessorAccess> getProcessorAccess(ContextAuthentication ca) {

        if (ca.isAuthenticated())
            return this.getSecurityService()
                    .getUserSubOrgInternal(
                            ca.getUser().getId(),
                            ca.getUrlAppCode(),
                            ca.getUser().getClientId())
                    .map(subOrg -> ProcessorAccess.of(ca, subOrg));

        return FlatMapUtil.flatMapMono(
                () -> SecurityContextUtil.resolveAppAndClientCode(null, null),
                acTup -> this.getSecurityService()
                        .appInheritance(acTup.getT1(), ca.getUrlClientCode(), acTup.getT2())
                        .map(clientCodes -> clientCodes.contains(acTup.getT2()))
                        .flatMap(BooleanUtil::safeValueOfWithEmpty)
                        .switchIfEmpty(this.getMsgService()
                                .throwMessage(
                                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                        ProcessorMessageResourceService.FORBIDDEN_APP_ACCESS,
                                        acTup.getT2())),
                (acTup, hasAppAccess) -> this.getSecurityService()
                        .isUserBeingManaged(ca.getUser().getId(), acTup.getT2())
                        .flatMap(BooleanUtil::safeValueOfWithEmpty)
                        .switchIfEmpty(this.getMsgService()
                                .throwMessage(
                                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                        ProcessorMessageResourceService.INVALID_USER_FOR_CLIENT,
                                        ca.getUser().getId(),
                                        acTup.getT2())),
                (acTup, hasAppAccess, isUserManaged) -> this.getSecurityService()
                        .getUserSubOrgInternal(
                                ca.getUser().getId(),
                                ca.getUrlAppCode(),
                                ca.getUser().getClientId()),
                (acTup, hasAppAccess, isUserManaged, userSubOrg) -> Mono.just(ProcessorAccess.of(
                        acTup.getT1(),
                        acTup.getT2(),
                        ULongUtil.valueOf(ca.getUser().getId()),
                        hasAppAccess && isUserManaged,
                        userSubOrg)));
    }
}
