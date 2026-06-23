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
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.billing.AppActionCostDAO;
import com.fincity.security.dao.billing.AppBillingConfigDAO;
import com.fincity.security.dto.billing.AppActionCost;
import com.fincity.security.jooq.tables.records.SecurityAppActionCostRecord;
import com.fincity.security.service.AppService;
import com.fincity.security.service.ClientService;
import com.fincity.security.service.SecurityMessageResourceService;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * CRUD for the per-action credit cost rows that hang off a billing config
 * (one config per app+client). The rates layer of {@link AppBillingConfigService}:
 * each row sets a {@code creditCost} and {@code actionClass} (ENGAGEMENT grace
 * vs METERED block) for one action key under a config; an action with no row is
 * free for that config.
 *
 * <p>Authorisation mirrors the config: the row's billing config resolves to an
 * (app, client) pair, and the caller needs {@code Application_CREATE} plus write
 * access to the app and management of that client. So the same app creator /
 * managing client that owns the config owns its rates.
 */
@Service
public class AppActionCostService extends
        AbstractJOOQUpdatableDataService<SecurityAppActionCostRecord, ULong, AppActionCost, AppActionCostDAO> {

    private final AppBillingConfigDAO appBillingConfigDAO;
    private final AppService appService;
    private final ClientService clientService;
    private final SecurityMessageResourceService messageResourceService;

    public AppActionCostService(AppActionCostDAO dao, AppBillingConfigDAO appBillingConfigDAO, AppService appService,
            ClientService clientService, SecurityMessageResourceService messageResourceService) {
        this.dao = dao;
        this.appBillingConfigDAO = appBillingConfigDAO;
        this.appService = appService;
        this.clientService = clientService;
        this.messageResourceService = messageResourceService;
    }

    @Override
    @PreAuthorize("hasAuthority('Authorities.Application_CREATE')")
    public Mono<AppActionCost> create(AppActionCost entity) {
        return this.assertCanManageConfig(entity.getBillingConfigId())
                .then(Mono.defer(() -> super.create(entity)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppActionCostService.create"));
    }

    @Override
    @PreAuthorize("hasAuthority('Authorities.Application_CREATE')")
    public Mono<AppActionCost> read(ULong id) {
        return super.read(id);
    }

    /** All cost rows under a billing config, for the rates editor. */
    @PreAuthorize("hasAuthority('Authorities.Application_CREATE')")
    public Mono<List<AppActionCost>> findByConfigId(ULong billingConfigId) {
        return this.dao.findByConfigId(billingConfigId)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppActionCostService.findByConfigId"));
    }

    @Override
    @PreAuthorize("hasAuthority('Authorities.Application_CREATE')")
    public Mono<AppActionCost> update(AppActionCost entity) {
        return this.read(entity.getId())
                .flatMap(existing -> this.assertCanManageConfig(existing.getBillingConfigId()))
                .then(Mono.defer(() -> super.update(entity)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppActionCostService.update"));
    }

    @Override
    @PreAuthorize("hasAuthority('Authorities.Application_CREATE')")
    public Mono<Integer> delete(ULong id) {
        return this.read(id)
                .flatMap(existing -> this.assertCanManageConfig(existing.getBillingConfigId()))
                .then(Mono.defer(() -> super.delete(id)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppActionCostService.delete"));
    }

    @Override
    protected Mono<AppActionCost> updatableEntity(AppActionCost entity) {
        return this.read(entity.getId()).map(existing -> existing
                .setActionKey(entity.getActionKey())
                .setCreditCost(entity.getCreditCost())
                .setActionClass(entity.getActionClass())
                .setFreeQuota(entity.getFreeQuota())
                .setStatus(entity.getStatus()));
    }

    /**
     * Resolve the row's billing config to its (app, client) and reuse the config's
     * authorisation: write access to the app + management of the target client.
     */
    private Mono<Boolean> assertCanManageConfig(ULong billingConfigId) {
        return FlatMapUtil.flatMapMono(

                () -> this.appBillingConfigDAO.readById(billingConfigId)
                        .switchIfEmpty(this.forbidden("the billing config")),

                config -> SecurityContextUtil.getUsersContextAuthentication()
                        .flatMap(ca -> this.appService
                                .hasWriteAccess(config.getAppId(), ULong.valueOf(ca.getUser().getClientId()))
                                .filter(BooleanUtil::safeValueOf)
                                .switchIfEmpty(this.forbidden("write access to the application"))
                                .flatMap(hasApp -> this.clientService.isUserClientManageClient(ca, config.getClientId())
                                        .filter(BooleanUtil::safeValueOf)
                                        .switchIfEmpty(this.forbidden("manage the target client")))))
                .map(x -> Boolean.TRUE);
    }

    private <T> Mono<T> forbidden(String what) {
        return this.messageResourceService.throwMessage(
                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                SecurityMessageResourceService.FORBIDDEN_PERMISSION, what);
    }
}
