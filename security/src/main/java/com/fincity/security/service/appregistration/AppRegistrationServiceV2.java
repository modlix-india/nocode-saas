package com.fincity.security.service.appregistration;

import java.time.LocalDateTime;
import java.util.Map;

import org.jooq.types.ULong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.appregistration.AppRegistrationV2DAO;
import com.fincity.security.dto.appregistration.AbstractAppRegistration;
import com.fincity.security.enums.AppRegistrationObjectType;
import com.fincity.security.enums.ClientLevelType;
import com.fincity.security.service.AppService;
import com.fincity.security.service.ClientService;
import com.fincity.security.service.ProfileService;
import com.fincity.security.service.RoleV2Service;
import com.fincity.security.service.SecurityMessageResourceService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class AppRegistrationServiceV2 implements IAppRegistrationHelperService {

    public static final String DEFAULT_BUSINESS_TYPE = "COMMON";

    private final SecurityMessageResourceService messageService;
    private final ClientService clientService;
    private final AppService appService;
    private final AppRegistrationV2DAO dao;

    private static final Logger logger = LoggerFactory.getLogger(AppRegistrationServiceV2.class);

    private final Map<Class<? extends IAppRegistrationHelperService>, IAppRegistrationHelperService> helperServices;

    public AppRegistrationServiceV2(SecurityMessageResourceService messageService, AppRegistrationV2DAO dao,
                                    ClientService clientService, AppService appService, ProfileService profileService,
                                    RoleV2Service roleV2Service) {
        this.messageService = messageService;
        this.dao = dao;
        this.clientService = clientService;
        this.appService = appService;
        this.helperServices = Map.of(
                AppService.class, this.appService,
                ProfileService.class, profileService,
                RoleV2Service.class, roleV2Service,
                AppRegistrationServiceV2.class, this);
    }

    // Returns a boolean if the internal ids are accessible by the client or empty
    // if not
    private Mono<Boolean> hasAccessToInnerIds(AppRegistrationObjectType type, ULong clientId,
                                              AbstractAppRegistration entity) {

        if (type.extraValues == null || type.extraValues.length == 0)
            return Mono.just(true);

        return Flux.fromArray(type.extraValues)
                .flatMap(extra -> {

                    ULong id = null;
                    try {
                        Object obj = type.pojoClass.getDeclaredMethod("get"
                                        + extra.idFieldName().substring(0, 1).toUpperCase()
                                        + extra.idFieldName().substring(1))
                                .invoke(entity);

                        if (obj == null)
                            return Mono.just(true);
                        id = (ULong) obj;
                    } catch (Exception ex) {
                        return Mono.just(Boolean.FALSE);
                    }

                    return this.helperServices.get(extra.helperServiceClass()).hasAccessTo(id,
                            clientId,
                            type);
                })
                .reduce(Boolean::logicalAnd)
                .filter(BooleanUtil::safeValueOf);
    }

    private Mono<AbstractAppRegistration> fillAppNClient(AbstractAppRegistration entity) {
        return FlatMapUtil.flatMapMono(

                () -> this.appService.read(entity.getAppId()),

                app -> this.clientService.read(entity.getClientId()),

                (app, client) -> Mono.just(entity.setApp(app).setClient(client))

        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "AppRegistrationServiceV2.fillAppNClient"));
    }

    private Mono<AbstractAppRegistration> fillObjects(AppRegistrationObjectType type,
                                                      AbstractAppRegistration entity) {
        if (type.extraValues == null || type.extraValues.length == 0)
            return this.fillAppNClient(entity);

        return Flux.fromArray(type.extraValues)
                .flatMap(extra -> {
                    String methodNameSuffix = extra.idFieldName().substring(0, 1).toUpperCase()
                            + extra.idFieldName().substring(1);
                    try {
                        Object obj = type.pojoClass.getDeclaredMethod("get" + methodNameSuffix)
                                .invoke(entity);

                        if (obj == null)
                            return Mono.empty();
                        return this.helperServices.get(extra.helperServiceClass())
                                .readObject((ULong) obj, type)
                                .map(fillerObject -> {
                                    try {
                                        String setMethodNameSuffix = extra.objectFieldName().substring(0, 1).toUpperCase()
                                                + extra.objectFieldName().substring(1);
                                        type.pojoClass.getDeclaredMethod("set"
                                                        + setMethodNameSuffix, fillerObject.getClass())
                                                .invoke(entity, fillerObject);
                                    } catch (Exception ex) {
                                        this.logger.error("Error while setting object field for {} in the object {} with value {}", extra, entity, fillerObject, ex);
                                    }
                                    return entity;
                                });
                    } catch (Exception ex) {
                        return Mono.empty();
                    }
                })
                .collectList()
                .map(e -> entity)
                .flatMap(this::fillAppNClient);
    }

    public Mono<AbstractAppRegistration> create(AppRegistrationObjectType type, String appCode,
                                                AbstractAppRegistration entity) {

        return FlatMapUtil.flatMapMono(

                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> this.appService.getAppByCode(appCode),

                        (ca, app) -> this.appService.hasWriteAccess(appCode, ca.getClientCode())
                                .filter(e -> e),

                        (ca, app, hasWriteAccess) -> this.hasAccessToInnerIds(type,
                                ULong.valueOf(ca.getUser().getClientId()),
                                entity),

                        (ca, app, hasWriteAccess, hasAccessToInnerIds) -> {

                            entity.setAppId(app.getId());
                            entity.setCreatedBy(ULong.valueOf(ca.getUser().getId()));
                            entity.setCreatedAt(LocalDateTime.now());

                            if (entity.getClientId() == null) {
                                entity.setClientId(ULong.valueOf(ca.getUser().getClientId()));
                                return Mono.just(true);
                            }

                            if (ca.isSystemClient())
                                Mono.just(true);

                            return this.clientService
                                    .isBeingManagedBy(ULong.valueOf(ca.getUser().getClientId()),
                                            entity.getClientId())
                                    .filter(e -> e);
                        },

                        (ca, app, hasWriteAccess, hasAccessToInnerIds, isBeingManaged) -> this.dao
                                .create(type, entity)
                                .flatMap(e -> this.fillObjects(type, e))

                )
                .switchIfEmpty(this.messageService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        SecurityMessageResourceService.FORBIDDEN_APP_REG_OBJECTS, "App Access"))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppRegistrationServiceV2.create"));
    }

    public Mono<AbstractAppRegistration> getById(AppRegistrationObjectType type, ULong id) {
        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.dao.getById(type, id),

                (ca, entity) -> ca.isSystemClient() ? Mono.just(true)
                        : this.appService
                        .hasWriteAccess(entity.getAppId(),
                                ULong.valueOf(ca.getUser()
                                        .getClientId()))
                        .filter(e -> e),

                (ca, entity, hasAccess) -> ca.isSystemClient() ? Mono.just(true)
                        : this.hasAccessToInnerIds(type,
                        ULong.valueOf(ca.getUser().getClientId()), entity),

                (ca, entity, hasAccess, hasAccessToInnerIds) -> ca.isSystemClient() ? Mono.just(true)
                        : this.clientService
                        .isBeingManagedBy(
                                ULong.valueOf(ca.getUser()
                                        .getClientId()),
                                entity.getClientId())
                        .filter(e -> e),

                (ca, entity, hasAccess, hasAccessToInnerIds, isBeingManaged) -> this.fillObjects(type,
                        entity)

        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "AppRegistrationServiceV2.getById"));
    }

    public Mono<Boolean> delete(AppRegistrationObjectType type, ULong id) {
        return FlatMapUtil.flatMapMono(

                        () -> this.getById(type, id),

                        entity -> this.dao.delete(type, id))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppRegistrationServiceV2.delete"));
    }

    public Mono<Page<? extends AbstractAppRegistration>> get(AppRegistrationObjectType type, String appCode,
                                                             String clientCode, ULong clientId,
                                                             String clientType, ClientLevelType level, String businessType, Pageable pageable) {

        if (clientCode != null && clientId != null)
            return this.messageService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    SecurityMessageResourceService.CLIENT_CODE_OR_ID_ONLY_ONE);

        return (Mono<Page<? extends AbstractAppRegistration>>) FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.appService.getAppByCode(appCode),

                (ca, app) -> ca.isSystemClient() ? Mono.just(true)
                        : this.appService.hasWriteAccess(appCode, ca.getClientCode())
                        .filter(e -> e),

                (ca, app, hasWriteAccess) -> {

                    if (!ca.isSystemClient() || (clientId == null && clientCode == null))
                        return Mono.just(ULong.valueOf(ca.getUser().getClientId()));

                    return (clientCode != null
                            ? this.clientService.getClientBy(clientCode).map(e -> e.getId())
                            : Mono.just(clientId))
                            .flatMap(id -> this.clientService
                                    .isBeingManagedBy(
                                            ULong.valueOf(ca.getUser()
                                                    .getClientId()),
                                            id)
                                    .filter(e -> e)
                                    .map(e -> id));
                },

                (ca, app, hasWriteAccess, newClientId) -> this.dao.get(type, app.getId(),
                        newClientId, clientType,
                        level, businessType, pageable),

                (ca, app, hasWriteAccess, newClientId, page) -> {
                    Mono<Page<? extends AbstractAppRegistration>> a = Flux.fromIterable(page.getContent())
                            .flatMap(e -> this.fillObjects(type, e)).collectList()
                            .<Page<? extends AbstractAppRegistration>>map(e -> page);

                    return a;
                }

        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "AppRegistrationServiceV2.get"));
    }

    @Override
    public Mono<? extends AbstractDTO<ULong, ULong>> readObject(ULong id,
                                                                AppRegistrationObjectType type) {

        return this.getById(type, id);
    }

    @Override
    public Mono<Boolean> hasAccessTo(ULong id, ULong clientId, AppRegistrationObjectType type) {
        return this.dao.getById(type, id)
                .map(e -> e.getClientId().equals(clientId));
    }
}
