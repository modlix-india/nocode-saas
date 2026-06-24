package com.fincity.security.service.billing;

import java.util.List;

import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.billing.AppBillingConfigDAO;
import com.fincity.security.dto.billing.AppBillingConfig;
import com.fincity.security.jooq.tables.records.SecurityAppBillingConfigRecord;
import com.fincity.security.model.billing.MeteringInstruction;
import com.fincity.security.service.AppService;
import com.fincity.security.service.ClientService;
import com.fincity.security.service.SecurityMessageResourceService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * CRUD for the per-(configurator client C, app) billing config. Mutations need
 * {@code Application_CREATE} plus write access to the app (by the caller and by
 * the config's client) and management of the config's client. Reads are
 * restricted to the caller's own + managed clients (no @PreAuthorize gate).
 */
@Service
public class AppBillingConfigService extends
        AbstractJOOQUpdatableDataService<SecurityAppBillingConfigRecord, ULong, AppBillingConfig, AppBillingConfigDAO> {

    private static final String CACHE_NAME_CONFIG_BY_CODES = "appBillingConfigByCodes";

    private final AppService appService;
    private final ClientService clientService;
    private final CacheService cacheService;
    private final SecurityMessageResourceService messageResourceService;

    public AppBillingConfigService(AppService appService, ClientService clientService, CacheService cacheService,
            SecurityMessageResourceService messageResourceService) {
        this.appService = appService;
        this.clientService = clientService;
        this.cacheService = cacheService;
        this.messageResourceService = messageResourceService;
    }

    @Override
    @PreAuthorize("hasAuthority('Authorities.Application_CREATE')")
    public Mono<AppBillingConfig> create(AppBillingConfig entity) {
        return this.assertCanManage(entity.getAppId(), entity.getClientId())
                .then(Mono.defer(() -> super.create(entity)))
                .flatMap(this::evictByConfig)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppBillingConfigService.create"));
    }

    @Override
    @PreAuthorize("hasAuthority('Authorities.Application_CREATE')")
    public Mono<AppBillingConfig> update(AppBillingConfig entity) {
        return this.dao.readById(entity.getId())
                .flatMap(existing -> this.assertCanManage(existing.getAppId(), existing.getClientId()))
                .then(Mono.defer(() -> super.update(entity)))
                .flatMap(this::evictByConfig)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppBillingConfigService.update"));
    }

    @Override
    @PreAuthorize("hasAuthority('Authorities.Application_CREATE')")
    public Mono<Integer> delete(ULong id) {
        return this.dao.readById(id)
                .flatMap(existing -> this.assertCanManage(existing.getAppId(), existing.getClientId())
                        .then(Mono.defer(() -> super.delete(id)))
                        .flatMap(count -> this.evictByConfig(existing).thenReturn(count)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppBillingConfigService.delete"));
    }

    /** Read restricted to the caller's own + managed clients (no auth gate). */
    @Override
    public Mono<AppBillingConfig> read(ULong id) {
        return FlatMapUtil.flatMapMono(
                () -> this.dao.readById(id),
                config -> this.canSee(config).filter(BooleanUtil::safeValueOf).map(x -> config))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppBillingConfigService.read"));
    }

    /** All configs for an app the caller can see (own + managed). For the Billing tab. */
    public Mono<List<AppBillingConfig>> findByApp(ULong appId) {
        return this.dao.findByApp(appId)
                .flatMapMany(Flux::fromIterable)
                .filterWhen(this::canSee)
                .collectList()
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppBillingConfigService.findByApp"));
    }

    /**
     * Resolve config(appCode, clientCode), cached even when absent, for the
     * serving-status path. Invalidated on a config change for that (client, app).
     */
    public Mono<AppBillingConfig> readByAppAndClient(String appCode, String clientCode) {
        return this.cacheService.cacheEmptyValueOrGet(CACHE_NAME_CONFIG_BY_CODES,
                () -> this.dao.findByAppCodeAndClientCode(appCode, clientCode), appCode, clientCode);
    }

    public Mono<AppBillingConfig> readByAppAndClientId(ULong appId, ULong clientId) {
        return this.dao.findByAppAndClient(appId, clientId);
    }

    /** Load a config by id and assert the caller may manage it (for bundle mutations). */
    public Mono<AppBillingConfig> assertManageableConfig(ULong configId) {
        return this.dao.readById(configId)
                .switchIfEmpty(this.forbidden("the billing config"))
                .flatMap(cfg -> this.assertCanManage(cfg.getAppId(), cfg.getClientId()).thenReturn(cfg));
    }

    /** Read a config by id without the caller-visibility filter (internal use). */
    public Mono<AppBillingConfig> readInternal(ULong configId) {
        return this.dao.readById(configId);
    }

    /** Billable (C, app, M) rows for a metered action (internal, metering services). */
    public Flux<MeteringInstruction> chargeInstructions(String actionKey) {
        return this.dao.chargeInstructions(actionKey);
    }

    private Mono<AppBillingConfig> evictByConfig(AppBillingConfig config) {
        return FlatMapUtil.flatMapMono(
                () -> this.appService.getAppById(config.getAppId()),
                app -> this.clientService.getClientInfoById(config.getClientId()),
                (app, client) -> this.cacheService.evict(CACHE_NAME_CONFIG_BY_CODES, app.getAppCode(),
                        client.getCode()))
                .thenReturn(config);
    }

    private Mono<Boolean> canSee(AppBillingConfig config) {
        return SecurityContextUtil.getUsersContextAuthentication().flatMap(ca -> {
            if (ca.isSystemClient()
                    || config.getClientId().equals(ULong.valueOf(ca.getUser().getClientId())))
                return Mono.just(Boolean.TRUE);
            return this.clientService.isUserClientManageClient(ca, config.getClientId());
        });
    }

    private Mono<Boolean> assertCanManage(ULong appId, ULong configClientId) {
        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.appService.hasWriteAccess(appId, ULong.valueOf(ca.getUser().getClientId()))
                        .filter(BooleanUtil::safeValueOf)
                        .switchIfEmpty(this.forbidden("write access to the application")),

                (ca, callerAccess) -> this.appService.hasWriteAccess(appId, configClientId)
                        .filter(BooleanUtil::safeValueOf)
                        .switchIfEmpty(this.forbidden("the config's client must have access to the application")),

                (ca, callerAccess, configAccess) -> this.clientService.isUserClientManageClient(ca, configClientId)
                        .filter(BooleanUtil::safeValueOf)
                        .switchIfEmpty(this.forbidden("manage the config's client")))
                .map(x -> Boolean.TRUE);
    }

    private <T> Mono<T> forbidden(String what) {
        return this.messageResourceService.throwMessage(
                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                SecurityMessageResourceService.FORBIDDEN_PERMISSION, what);
    }

    @Override
    protected Mono<AppBillingConfig> updatableEntity(AppBillingConfig entity) {
        return this.read(entity.getId()).map(existing -> existing
                .setAppRentPerMonth(entity.getAppRentPerMonth())
                .setSiteRentPerMonth(entity.getSiteRentPerMonth())
                .setFilesTokensPerMonth(entity.getFilesTokensPerMonth())
                .setStorageRowTokensPerMonth(entity.getStorageRowTokensPerMonth())
                .setDealTokensPerMonth(entity.getDealTokensPerMonth())
                .setUserTokensPerMonth(entity.getUserTokensPerMonth())
                .setAiTokensPerMillion(entity.getAiTokensPerMillion())
                .setFreeApps(entity.getFreeApps())
                .setFreeSites(entity.getFreeSites())
                .setFreeFilesGb(entity.getFreeFilesGb())
                .setFreeStorageRows(entity.getFreeStorageRows())
                .setFreeDeals(entity.getFreeDeals())
                .setFreeUsers(entity.getFreeUsers())
                .setFreeAiTokensPerMonth(entity.getFreeAiTokensPerMonth())
                .setGstPercentage(entity.getGstPercentage())
                .setPaymentGateway(entity.getPaymentGateway())
                .setPaymentGatewayConfig(entity.getPaymentGatewayConfig())
                .setSellerLegalName(entity.getSellerLegalName())
                .setSellerGstin(entity.getSellerGstin())
                .setSellerAddress(entity.getSellerAddress())
                .setLowBalanceThreshold(entity.getLowBalanceThreshold())
                .setSuspendAppCode(entity.getSuspendAppCode())
                .setSuspendClientCode(entity.getSuspendClientCode())
                .setStatus(entity.getStatus()));
    }
}
