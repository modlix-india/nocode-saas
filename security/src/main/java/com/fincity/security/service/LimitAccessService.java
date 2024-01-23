package com.fincity.security.service;

import java.util.HashMap;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.LimitAccessDAO;
import com.fincity.security.dto.LimitAccess;
import com.fincity.security.jooq.tables.records.SecurityAppLimitationsRecord;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class LimitAccessService
        extends AbstractJOOQUpdatableDataService<SecurityAppLimitationsRecord, ULong, LimitAccess, LimitAccessDAO> {

    private static final String LIMIT = "limit";

    private static final String SEPERATOR = "_";

    @Autowired
    private CacheService cacheService;

    @Autowired
    @Lazy
    private AppService appService;

    @Autowired
    private SecurityMessageResourceService messageResourceService;

    @Autowired
    @Lazy
    private ClientService clientService;

    @Override
    protected Mono<LimitAccess> updatableEntity(LimitAccess entity) {

        return this.read(entity.getId())
                .map(e -> e.setLimit(entity.getLimit()));
    }

    @Override
    protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {

        HashMap<String, Object> map = new HashMap<>();

        if (fields == null)
            return Mono.just(map);

        map.put(LIMIT, fields.get(LIMIT));
        return Mono.just(map);
    }

    public Mono<Long> readByAppandClientId(ULong appId, ULong clientId, String objectName, ULong urlClientId) {

        return cacheService.cacheValueOrGet(appId.toString() + SEPERATOR + clientId.toString() + SEPERATOR + objectName,
                () -> this.dao.getByAppandClientId(appId, clientId, objectName, urlClientId));

    }

    @Override
    @PreAuthorize("hasAuthority('Authorities.Limitations_CREATE')")
    public Mono<LimitAccess> create(LimitAccess entity) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.appService.read(entity.getAppId()),

                (ca, app) -> {

                    if (ca.isSystemClient())
                        return Mono.just(true);

                    if (ca.getUser().getClientId().equals(app.getClientId().toBigInteger()))
                        return Mono.just(true);

                    return this.appService
                            .hasWriteAccess(entity.getAppId(), ULongUtil.valueOf(ca.getUser().getClientId()))
                            .flatMap(e -> Mono.justOrEmpty(e.booleanValue() ? e : null));
                },

                (ca, app, hasAccess) -> {

                    if (entity.getClientId().equals(app.getClientId()))
                        return Mono.just(true);

                    return this.appService.hasWriteAccess(entity.getAppId(), entity.getClientId())
                            .flatMap(e -> Mono.justOrEmpty(e.booleanValue() ? e : null));
                },

                (ca, app, hasAccess, hasWriteAccess) -> {
                    if (ca.isSystemClient())
                        return Mono.just(true);

                    return this.clientService.isBeingManagedBy(ULongUtil.valueOf(ca.getUser().getClientId()),
                            entity.getClientId());
                },
                (ca, app, hasAccess, hasWriteAccess, isBeingManaged) -> isBeingManaged.booleanValue()
                        ? super.create(entity)
                        : Mono.empty())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "LimitAccessService.create"))
                .switchIfEmpty(
                        this.messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                SecurityMessageResourceService.FORBIDDEN_CREATE, LIMIT));

    }

    @Override
    @PreAuthorize("hasAuthority('Authorities.Limitations_UPDATE')")
    public Mono<LimitAccess> update(LimitAccess entity) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.appService.read(entity.getAppId()),

                (ca, app) -> {

                    if (ca.isSystemClient())
                        return Mono.just(true);

                    if (ca.getUser().getClientId().equals(app.getClientId().toBigInteger()))
                        return Mono.just(true);

                    return this.appService
                            .hasWriteAccess(entity.getAppId(), ULongUtil.valueOf(ca.getUser().getClientId()))
                            .flatMap(e -> Mono.justOrEmpty(e.booleanValue() ? e : null));
                },

                (ca, app, hasAccess) -> {

                    if (entity.getClientId().equals(app.getClientId()))
                        return Mono.just(true);

                    return this.appService.hasWriteAccess(entity.getAppId(), entity.getClientId())
                            .flatMap(e -> Mono.justOrEmpty(e.booleanValue() ? e : null));
                },

                (ca, app, hasAccess, hasWriteAccess) -> {
                    if (ca.isSystemClient())
                        return Mono.just(true);

                    return this.clientService.isBeingManagedBy(ULongUtil.valueOf(ca.getUser().getClientId()),
                            entity.getClientId());
                },
                (ca, app, hasAccess, hasWriteAccess, isBeingManaged) -> isBeingManaged.booleanValue()
                        ? super.create(entity)
                        : Mono.empty())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "LimitAccessService.update"))
                .switchIfEmpty(
                        this.messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                SecurityMessageResourceService.FORBIDDEN_UPDATE, LIMIT));

    }

    @Override
    @PreAuthorize("hasAuthority('Authorities.Limitations_DELETE')")
    public Mono<Integer> delete(ULong id) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.read(id),

                (ca, entity) -> this.appService.read(entity.getAppId()),

                (ca, entity, app) -> {

                    if (ca.isSystemClient())
                        return Mono.just(true);

                    if (ca.getUser().getClientId().equals(app.getClientId().toBigInteger()))
                        return Mono.just(true);

                    return this.appService
                            .hasDeleteAccess(entity.getAppId())
                            .flatMap(e -> Mono.justOrEmpty(e.booleanValue() ? e : null));
                },

                (ca, entity, app, hasAccess) -> {

                    if (entity.getClientId().equals(app.getClientId()))
                        return Mono.just(true);

                    return this.appService.hasDeleteAccess(entity.getAppId())
                            .flatMap(e -> Mono.justOrEmpty(e.booleanValue() ? e : null));
                },

                (ca, entity, app, hasAccess, hasWriteAccess) -> {
                    if (ca.isSystemClient())
                        return Mono.just(true);

                    return this.clientService.isBeingManagedBy(ULongUtil.valueOf(ca.getUser().getClientId()),
                            entity.getClientId());
                },
                (ca, entity, app, hasAccess, hasWriteAccess, isBeingManaged) -> isBeingManaged.booleanValue()
                        ? super.delete(id)
                        : Mono.empty())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "LimitAccessService.delete"))
                .switchIfEmpty(
                        this.messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                SecurityMessageResourceService.FORBIDDEN_DELETE, LIMIT));

    }

}
