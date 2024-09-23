package com.fincity.security.service.appregistration;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.AppRegistrationIntegrationDAO;
import com.fincity.security.dto.AppRegistrationIntegration;
import com.fincity.security.jooq.tables.records.SecurityAppRegIntegrationRecord;
import com.fincity.security.service.AppService;
import java.util.Map;
import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class AppRegistrationIntegrationService
        extends
        AbstractJOOQUpdatableDataService<SecurityAppRegIntegrationRecord, ULong, AppRegistrationIntegration, AppRegistrationIntegrationDAO> {

    private static final String SCOPES = "scopes";

    private static final String CACHE_NAME_INTEGRATION_ID = "integrationId";

    private final AppService appService;
    private final CacheService cacheService;

    public AppRegistrationIntegrationService(AppService appService, CacheService cacheService) {
        this.appService = appService;
        this.cacheService = cacheService;
    }

    @Override
    @PreAuthorize("hasAuthority('Authorities.Integration_CREATE')")
    public Mono<AppRegistrationIntegration> create(AppRegistrationIntegration entity) {
        return super.create(entity);
    }

    @Override
    @PreAuthorize("hasAuthority('Authorities.Integration_READ')")
    public Mono<AppRegistrationIntegration> read(ULong id) {
        return super.read(id);
    }

    @Override
    @PreAuthorize("hasAuthority('Authorities.Integration_UPDATE')")
    public Mono<AppRegistrationIntegration> update(AppRegistrationIntegration entity) {
        return super.update(entity)
                .flatMap(this.cacheService.evictFunctionWithFunctionMultipleKeys(CACHE_NAME_INTEGRATION_ID,
                        e -> new Object[] {
                                e.getAppId(), "-", e.getClientId()
                        }));
    }

    @Override
    @PreAuthorize("hasAuthority('Authorities.Integration_DELETE')")
    public Mono<Integer> delete(ULong id) {

        return FlatMapUtil.flatMapMono(

                () -> this.read(id),
                e -> super.delete(id).flatMap(this.cacheService.evictFunction(CACHE_NAME_INTEGRATION_ID,
                        e.getAppId(), "-", e.getClientId())))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppRegistrationIntegrationService.delete"));
    }

    @Override
    @PreAuthorize("hasAuthority('Authorities.Integration_READ')")
    public Mono<Page<AppRegistrationIntegration>> readPageFilter(Pageable pageable, AbstractCondition condition) {
        return super.readPageFilter(pageable, condition);
    }

    protected Mono<AppRegistrationIntegration> updatableEntity(AppRegistrationIntegration entity) {
        return FlatMapUtil.flatMapMono(
                () -> this.read(entity.getId()),

                existing -> Mono.just(existing.setScopes(entity.getScopes())))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppRegistrationIntegrationService.updatableEntity"));
    }

    @Override
    protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {
        if (fields.containsKey(SCOPES))
            return Mono.just(Map.of(SCOPES, fields.get(SCOPES)));

        return Mono.just(Map.of());
    }

    public Mono<ULong> getIntegrationId() {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.appService.getAppByCode(ca.getUrlAppCode()),

                (ca, app) -> this.cacheService.cacheValueOrGet(CACHE_NAME_INTEGRATION_ID,
                        () -> this.dao.getIntegrationId(app.getId(), ULong.valueOf(ca.getLoggedInFromClientId())),
                        app.getId(), "-", ca.getLoggedInFromClientId()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppRegistrationIntegrationService.getIntegrationId"));
    }
}
