package com.fincity.security.service.billing;

import java.util.List;

import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.billing.AppBillingBundleDAO;
import com.fincity.security.dto.billing.AppBillingBundle;
import com.fincity.security.jooq.tables.records.SecurityAppBillingBundleRecord;

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

    public AppBillingBundleService(AppBillingConfigService configService) {
        this.configService = configService;
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
