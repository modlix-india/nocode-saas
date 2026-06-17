package com.fincity.security.service.billing;

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
import com.fincity.security.dao.billing.AppBillingConfigDAO;
import com.fincity.security.dto.billing.AppBillingConfig;
import com.fincity.security.jooq.tables.records.SecurityAppBillingConfigRecord;
import com.fincity.security.service.AppService;
import com.fincity.security.service.ClientService;
import com.fincity.security.service.SecurityMessageResourceService;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * CRUD for per-(app, client) billing config. Billing is resolved by the URL's
 * client context {@code (app, urlClient)}; no row for a client means no
 * enforcement for that client's users.
 *
 * <p>Mutations are allowed for application creators (not just platform admins):
 * the caller needs the matching authority, write access to the app, and
 * management of the target client via the client hierarchy. So a managing
 * client (e.g. BabuRetailer) can configure billing for a managed client
 * (e.g. RajuAgency) provided its user has write access to the app.
 */
@Service
public class AppBillingConfigService extends
        AbstractJOOQUpdatableDataService<SecurityAppBillingConfigRecord, ULong, AppBillingConfig, AppBillingConfigDAO> {

    private final AppService appService;
    private final ClientService clientService;
    private final SecurityMessageResourceService messageResourceService;

    public AppBillingConfigService(AppBillingConfigDAO dao, AppService appService, ClientService clientService,
            SecurityMessageResourceService messageResourceService) {
        this.dao = dao;
        this.appService = appService;
        this.clientService = clientService;
        this.messageResourceService = messageResourceService;
    }

    @Override
    @PreAuthorize("hasAuthority('Authorities.Billing_Config_CREATE')")
    public Mono<AppBillingConfig> create(AppBillingConfig entity) {
        return this.assertCanManage(entity.getAppId(), entity.getClientId())
                .then(Mono.defer(() -> super.create(entity)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppBillingConfigService.create"));
    }

    @Override
    @PreAuthorize("hasAuthority('Authorities.Billing_Config_READ')")
    public Mono<AppBillingConfig> read(ULong id) {
        return super.read(id);
    }

    @PreAuthorize("hasAuthority('Authorities.Billing_Config_READ')")
    public Mono<AppBillingConfig> findByAppIdAndClientId(ULong appId, ULong clientId) {
        return this.dao.findByAppIdAndClientId(appId, clientId);
    }

    @Override
    @PreAuthorize("hasAuthority('Authorities.Billing_Config_UPDATE')")
    public Mono<AppBillingConfig> update(AppBillingConfig entity) {
        return this.read(entity.getId())
                .flatMap(existing -> this.assertCanManage(existing.getAppId(), existing.getClientId()))
                .then(Mono.defer(() -> super.update(entity)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppBillingConfigService.update"));
    }

    @Override
    @PreAuthorize("hasAuthority('Authorities.Billing_Config_DELETE')")
    public Mono<Integer> delete(ULong id) {
        return this.read(id)
                .flatMap(existing -> this.assertCanManage(existing.getAppId(), existing.getClientId()))
                .then(Mono.defer(() -> super.delete(id)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppBillingConfigService.delete"));
    }

    @Override
    protected Mono<AppBillingConfig> updatableEntity(AppBillingConfig entity) {
        return this.read(entity.getId()).map(existing -> existing
                .setDefaultPaymentGateway(entity.getDefaultPaymentGateway())
                .setSeatBillingEnabled(entity.isSeatBillingEnabled())
                .setSeatTokensPerMonth(entity.getSeatTokensPerMonth())
                .setMonthlyFreeTokens(entity.getMonthlyFreeTokens())
                .setEnforced(entity.isEnforced())
                .setStatus(entity.getStatus()));
    }

    /**
     * Caller must have write access to the app and manage the target client
     * (their own client passes the manage check trivially).
     */
    private Mono<Boolean> assertCanManage(ULong appId, ULong clientId) {
        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.appService.hasWriteAccess(appId, ULong.valueOf(ca.getUser().getClientId()))
                        .filter(BooleanUtil::safeValueOf)
                        .switchIfEmpty(this.forbidden("write access to the application")),

                (ca, hasApp) -> this.clientService.isUserClientManageClient(ca, clientId)
                        .filter(BooleanUtil::safeValueOf)
                        .switchIfEmpty(this.forbidden("manage the target client")))
                .map(x -> Boolean.TRUE);
    }

    private <T> Mono<T> forbidden(String what) {
        return this.messageResourceService.throwMessage(
                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                SecurityMessageResourceService.FORBIDDEN_PERMISSION, what);
    }
}
