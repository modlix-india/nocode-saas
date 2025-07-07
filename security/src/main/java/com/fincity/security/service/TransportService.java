package com.fincity.security.service;

import org.springframework.stereotype.Service;

import com.fincity.security.model.TransportPOJO;

import reactor.core.publisher.Mono;

@Service
public class TransportService {

    private final AppService appService;
    private final RoleV2Service roleService;
    private final ProfileService profileService;

    public TransportService(AppService appService, RoleV2Service roleService,
            ProfileService profileService) {
        this.appService = appService;
        this.roleService = roleService;
        this.profileService = profileService;
    }

    public Mono<TransportPOJO> makeTransport(String appCode) {

        return Mono.<TransportPOJO>empty();

        // return FlatMapUtil.flatMapMono(

        // SecurityContextUtil::getUsersContextAuthentication,

        // ca -> this.appService.getAppByCode(appCode),

        // (ca, app) -> this.appService.hasWriteAccess(appCode, ca.getClientCode()),

        // (ca, app, hasWriteAccess) -> {

        // if (!hasWriteAccess.booleanValue())
        // return Mono.<Boolean>empty();

        // TransportPOJO transport = new TransportPOJO();

        // transport.setUniqueTransportCode(UniqueUtil.shortUUID());
        // transport.setAppCode(app.getAppCode());
        // transport.setName(app.getAppName());
        // transport.setType(app.getAppType().toString());

        // return Mono.just(transport);
        // },

        // (ca, app, hasWriteAccess, transport) -> Mono.zip(
        // this.roleService.readForTransport(app.getId(), app.getClientId(),
        // ULong.valueOf(ca.getUser().getClientId())),
        // this.packageService.readForTransport(app.getId(), app.getClientId(),
        // ULong.valueOf(ca.getUser().getClientId()))),

        // (ca, app, hasWriteAccess, transport, tuple) -> {

        // transport.setRoles(tuple.getT1());
        // transport.setPackages(tuple.getT2());

        // return Mono.just(transport);
        // }

        // ).contextWrite(Context.of(LogUtil.METHOD_NAME,
        // "TransportService.makeTransport"));
    }

    public Mono<Boolean> createAndApply(TransportPOJO pojo) {

        return Mono.<Boolean>empty();

        // return FlatMapUtil.flatMapMono(

        // SecurityContextUtil::getUsersContextAuthentication,

        // ca -> this.appService.getAppByCode(pojo.getAppCode()),

        // (ca, app) -> this.appService.hasWriteAccess(pojo.getAppCode(),
        // pojo.getClientCode()),

        // (ca, app, hasWriteAccess) ->
        // this.roleService.createRolesFromTransport(app.getId(), pojo.getRoles()),

        // (ca, app, hasWriteAccess, createdRoles) ->
        // this.packageService.createPackagesFromTransport(app.getId(),
        // pojo.getPackages(), createdRoles),

        // (ca, app, hasWriteAccess, createdRoles, createdPackages) -> this.appService
        // .createPropertiesFromTransport(app.getId(),
        // pojo.getProperties())

        // ).contextWrite(Context.of(LogUtil.METHOD_NAME,
        // "TransportService.createAndApply"));
    }

}
