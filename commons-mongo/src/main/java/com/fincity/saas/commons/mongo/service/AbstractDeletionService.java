package com.fincity.saas.commons.mongo.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.List;

public abstract class AbstractDeletionService {

    private final IFeignSecurityService securityService;

    public abstract List<AbstractOverridableDataService<?, ?>> getServices();

    protected AbstractDeletionService(IFeignSecurityService securityService) {
        this.securityService = securityService;
    }

    public Mono<Boolean> deleteEverything(
        String forwardedHost,
        String forwardedPort,
        String clientCode,
        String headerAppCode,
        String appCode) {

        return FlatMapUtil.flatMapMono(

            SecurityContextUtil::getUsersContextAuthentication,

            ca -> this.securityService.hasDeleteAccess(ca.getAccessToken(),
                forwardedHost,
                forwardedPort,
                clientCode,
                headerAppCode,
                appCode, ca.getClientCode()).filter(BooleanUtil::safeValueOf),

            (ca, hasAccess) -> Flux.fromIterable(this.getServices())
                .flatMap(e -> e.deleteEverything(appCode, ca.getClientCode()))
                .reduce((a, b) -> true)
        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractDeletionService.deleteEverything"));
    }
}
