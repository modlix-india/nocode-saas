package com.fincity.security.service;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.UniqueUtil;
import com.fincity.security.model.TransportPOJO;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class TransportService {

    private final AppService appService;
    private final RoleService roleService;
    private final PackageService packageService;

    public TransportService(AppService appService, RoleService roleService,
            PackageService packageService) {
        this.appService = appService;
        this.roleService = roleService;
        this.packageService = packageService;
    }

    public Mono<TransportPOJO> makeTransport(String appCode) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.appService.getAppByCode(appCode),

                (ca, app) -> this.appService.hasWriteAccess(appCode, ca.getClientCode()),

                (ca, app, hasWriteAccess) -> {

                    if (!hasWriteAccess.booleanValue())
                        return Mono.empty();

                    TransportPOJO transport = new TransportPOJO();

                    transport.setUniqueTransportCode(UniqueUtil.shortUUID());
                    transport.setAppCode(app.getAppCode());
                    transport.setName(app.getAppName());
                    transport.setType(app.getAppType().toString());

                    return Mono.just(transport);
                },

                (ca, app, hasWriteAccess, transport) -> Mono.zip(
                        this.roleService.readForTransport(app.getId(), app.getClientId(),
                                ULong.valueOf(ca.getUser().getClientId())),
                        this.packageService.readForTransport(app.getId(), app.getClientId(),
                                ULong.valueOf(ca.getUser().getClientId()))),

                (ca, app, hasWriteAccess, transport, tuple) -> {

                    transport.setRoles(tuple.getT1());
                    transport.setPackages(tuple.getT2());

                    return Mono.just(transport);
                }

        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "TransportService.makeTransport"));
    }

}
