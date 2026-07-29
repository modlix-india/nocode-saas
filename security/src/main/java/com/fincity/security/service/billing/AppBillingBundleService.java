package com.fincity.security.service.billing;

import java.util.List;

import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.billing.AppBillingBundleDAO;
import com.fincity.security.dto.billing.AppBillingBundle;
import com.fincity.security.dto.billing.AppBillingConfig;
import com.fincity.security.jooq.enums.SecurityAppBillingBundleStatus;
import com.fincity.security.jooq.tables.records.SecurityAppBillingBundleRecord;
import com.fincity.security.service.AppService;
import com.fincity.security.service.ClientHierarchyService;
import com.fincity.security.service.ClientService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * CRUD for token bundles under a billing config. Mutations require the same
 * authority + management of the owning config as the config itself; the bundle
 * list is readable (it is what a buyer picks from).
 */
@Service
public class AppBillingBundleService extends
        AbstractJOOQUpdatableDataService<SecurityAppBillingBundleRecord, ULong, AppBillingBundle, AppBillingBundleDAO> {

    private final AppBillingConfigService configService;
    private final AppService appService;
    private final ClientService clientService;
    private final ClientHierarchyService clientHierarchyService;

    public AppBillingBundleService(AppBillingConfigService configService, AppService appService,
            ClientService clientService, ClientHierarchyService clientHierarchyService) {
        this.configService = configService;
        this.appService = appService;
        this.clientService = clientService;
        this.clientHierarchyService = clientHierarchyService;
    }

    @Override
    @PreAuthorize("hasAuthority('Authorities.Application_CREATE')")
    public Mono<AppBillingBundle> create(AppBillingBundle entity) {
        return this.configService.assertManageableConfig(entity.getBillingConfigId())
                .then(Mono.defer(() -> super.create(entity)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppBillingBundleService.create"));
    }

    @Override
    @PreAuthorize("hasAuthority('Authorities.Application_CREATE')")
    public Mono<AppBillingBundle> update(AppBillingBundle entity) {
        return this.dao.readById(entity.getId())
                .flatMap(existing -> this.configService.assertManageableConfig(existing.getBillingConfigId()))
                .then(Mono.defer(() -> super.update(entity)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppBillingBundleService.update"));
    }

    @Override
    @PreAuthorize("hasAuthority('Authorities.Application_CREATE')")
    public Mono<Integer> delete(ULong id) {
        return this.dao.readById(id)
                .flatMap(existing -> this.configService.assertManageableConfig(existing.getBillingConfigId())
                        .then(Mono.defer(() -> super.delete(id))))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppBillingBundleService.delete"));
    }

    public Mono<List<AppBillingBundle>> findByConfigId(ULong billingConfigId) {
        return this.dao.findByConfigId(billingConfigId);
    }

    /**
     * Public buyer view: the ACTIVE bundles for the app hosted under (appCode,
     * urlClientCode), resolved from the URL - no config id is taken from the caller
     * and the config itself (which carries rates + gateway secrets) is never
     * exposed. The config is the one owned by the URL client OR, failing that, the
     * nearest managing client up its hierarchy (so a buyer under a sub-client still
     * sees the owner's bundles). Empty list when no config governs that client.
     */
    public Mono<List<AppBillingBundle>> findServingByCodes(String appCode, String clientCode) {
        return FlatMapUtil.flatMapMono(
                () -> this.appService.getAppByCode(appCode),
                app -> this.clientService.getClientBy(clientCode),
                (app, client) -> this.resolveConfigForClient(app.getId(), client.getId()),
                (app, client, config) -> this.dao.findByConfigId(config.getId()))
                .map(bundles -> bundles.stream()
                        .filter(b -> b.getStatus() == SecurityAppBillingBundleStatus.ACTIVE)
                        .toList())
                .defaultIfEmpty(List.of())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppBillingBundleService.findServingByCodes"));
    }

    /**
     * The config governing (app, client): the config owned by the client, else the
     * nearest ANCESTOR up its hierarchy. `getClientHierarchyIdInOrder` returns
     * [self, level0-manager, level1-manager, ...], so a buyer under a sub-client
     * (e.g. FIN) resolves to the hosting owner's config (e.g. SYSTEM) that actually
     * governs it. (getManagingClientIds is the wrong direction - it returns the
     * clients this client manages, i.e. descendants.)
     */
    private Mono<AppBillingConfig> resolveConfigForClient(ULong appId, ULong clientId) {
        return this.clientHierarchyService.getClientHierarchyIdInOrder(clientId)
                .flatMapMany(Flux::fromIterable)
                .concatMap(ancestorId -> this.configService.readByAppAndClientId(appId, ancestorId))
                .next();
    }

    /** Ungated read for the purchase flow (the buyer is picking a bundle to pay for). */
    public Mono<AppBillingBundle> readForPurchase(ULong id) {
        return this.dao.readById(id);
    }

    /**
     * Blocked: an unscoped list would leak every config's bundles, and the per-row
     * visibility filter is not implemented for these generic queries. Use
     * {@link #findByConfigId(ULong)} (scoped to one config) instead.
     */
    @Override
    public Mono<Page<AppBillingBundle>> readPageFilter(Pageable pageable, AbstractCondition condition) {
        return Mono.error(new GenericException(HttpStatus.FORBIDDEN, "Listing all billing bundles is not permitted"));
    }

    /** Blocked for the same reason as {@link #readPageFilter}. */
    @Override
    public Flux<AppBillingBundle> readAllFilter(AbstractCondition condition) {
        return Flux.error(new GenericException(HttpStatus.FORBIDDEN, "Listing all billing bundles is not permitted"));
    }

    @Override
    protected Mono<AppBillingBundle> updatableEntity(AppBillingBundle entity) {
        return this.dao.readById(entity.getId()).map(existing -> existing
                .setLabel(entity.getLabel())
                .setBundleType(entity.getBundleType())
                .setTokens(entity.getTokens())
                .setPrice(entity.getPrice())
                .setPricePerToken(entity.getPricePerToken())
                .setMinTokens(entity.getMinTokens())
                .setMaxTokens(entity.getMaxTokens())
                .setCurrency(entity.getCurrency())
                .setStatus(entity.getStatus())
                .setDisplayOrder(entity.getDisplayOrder()));
    }
}
