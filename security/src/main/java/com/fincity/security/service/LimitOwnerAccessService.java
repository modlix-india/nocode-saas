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
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.security.dao.LimitOwnerAccessDAO;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.LimitAccess;
import com.fincity.security.jooq.tables.records.SecurityAppOwnerLimitationsRecord;

import reactor.core.publisher.Mono;

@Service
public class LimitOwnerAccessService extends
        AbstractJOOQUpdatableDataService<SecurityAppOwnerLimitationsRecord, ULong, LimitAccess, LimitOwnerAccessDAO> {

    private static final String LIMIT = "limit";

    private static final String SEPERATOR = "_";

    // We are using this cache in feignAuthenticationService
    private static final String CACHE_NAME_OBJECT_LIMIT = "objectLimit";

    @Autowired
    private CacheService cacheService;

    @Autowired
    @Lazy
    private AppService appService;

    @Autowired
    @Lazy
    private ClientService clientService;

    @Autowired
    private SecurityMessageResourceService messageResourceService;

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

                    return Mono.empty();
                },
                (ca, app, isValid) -> super.create(entity),

                (ca, app, isValid, object) -> this.clientService
                        .readInternal(entity.getClientId()).map(Client::getCode),

                (ca, app, isValid, object, clientCode) -> {
                    Mono<Boolean> eviction = this.cacheService
                            .evict(CACHE_NAME_OBJECT_LIMIT + "-" + entity.getName() + "-"
                                    + clientCode + "-" + app.getAppCode(), clientCode);
                    return eviction.map(x -> object);

                })
                .switchIfEmpty(this.messageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        SecurityMessageResourceService.ONLY_SYS_USER_ACTION, "create", entity.getName()));

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

                    return Mono.empty();
                },
                (ca, app, isValid) -> super.update(entity))
                .switchIfEmpty(this.messageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        SecurityMessageResourceService.ONLY_SYS_USER_ACTION, "update", entity.getName()));

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

                    return Mono.empty();
                },
                (ca, entity, app, isValid) -> super.delete(id))
                .switchIfEmpty(this.messageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        SecurityMessageResourceService.ONLY_SYS_USER_ACTION, "delete", ""));

    }

    public Mono<Long> readByAppandClientId(ULong appId, ULong clientId, String objectName) {

        return this.cacheService.cacheValueOrGet(
                appId.toString() + SEPERATOR + clientId.toString() + SEPERATOR + objectName,
                () -> this.dao.getByAppandClientId(appId, clientId, objectName));

    }
}
