package com.fincity.security.service.billing;

import org.jooq.types.ULong;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.billing.BillingProfileDAO;
import com.fincity.security.dto.billing.BillingProfile;
import com.fincity.security.service.AppService;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * The AUTHENTICATED buyer's own billing profile per (client, app). Read to
 * pre-fill the order-summary form; upserted when they save it. Gated to
 * ROLE_Owner so only the client's owner manages its billing details, and still
 * scoped to the caller's client from the security context, so a caller only ever
 * reads/writes their OWN profile (never another client's).
 */
@Service
public class BillingProfileService {

    private final BillingProfileDAO dao;
    private final AppService appService;

    public BillingProfileService(BillingProfileDAO dao, AppService appService) {
        this.dao = dao;
        this.appService = appService;
    }

    /** The caller's billing profile for the app in context (empty if none saved yet). Owner only. */
    @PreAuthorize("hasAuthority('Authorities.ROLE_Owner')")
    public Mono<BillingProfile> getMyProfile(String appCode) {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> this.appService.getAppByCode(appCode),
                (ca, app) -> this.dao.findByClientAndApp(ULong.valueOf(ca.getUser().getClientId()), app.getId()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "BillingProfileService.getMyProfile"));
    }

    /** Upsert the caller's billing profile for the app in context. Owner only; owner client always from context. */
    @PreAuthorize("hasAuthority('Authorities.ROLE_Owner')")
    public Mono<BillingProfile> saveMyProfile(String appCode, BillingProfile input) {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> this.appService.getAppByCode(appCode),
                (ca, app) -> {
                    ULong clientId = ULong.valueOf(ca.getUser().getClientId());
                    return this.dao.findByClientAndApp(clientId, app.getId())
                            .flatMap(existing -> this.dao.update(copyFields(existing, input)))
                            .switchIfEmpty(Mono.defer(() -> this.dao.create(
                                    copyFields(new BillingProfile().setClientId(clientId).setAppId(app.getId()),
                                            input))));
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "BillingProfileService.saveMyProfile"));
    }

    /** Copy the editable buyer fields from the request onto the target (clientId/appId are never taken from input). */
    private static BillingProfile copyFields(BillingProfile target, BillingProfile input) {
        return target
                .setLegalName(input.getLegalName())
                .setGstin(input.getGstin())
                .setAddressLine(input.getAddressLine())
                .setCity(input.getCity())
                .setState(input.getState())
                .setCountry(input.getCountry())
                .setPostalCode(input.getPostalCode());
    }
}
