package com.fincity.security.dao.billing;

import static com.fincity.security.jooq.Tables.SECURITY_APP_BILLING_BUNDLE;

import java.util.List;

import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.security.dto.billing.AppBillingBundle;
import com.fincity.security.jooq.enums.SecurityAppBillingBundleStatus;
import com.fincity.security.jooq.tables.records.SecurityAppBillingBundleRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class AppBillingBundleDAO
        extends AbstractUpdatableDAO<SecurityAppBillingBundleRecord, ULong, AppBillingBundle> {

    protected AppBillingBundleDAO() {
        super(AppBillingBundle.class, SECURITY_APP_BILLING_BUNDLE, SECURITY_APP_BILLING_BUNDLE.ID);
    }

    public Mono<List<AppBillingBundle>> findByConfigId(ULong billingConfigId) {
        return Flux.from(this.dslContext.selectFrom(SECURITY_APP_BILLING_BUNDLE)
                .where(SECURITY_APP_BILLING_BUNDLE.BILLING_CONFIG_ID.eq(billingConfigId))
                .and(SECURITY_APP_BILLING_BUNDLE.STATUS.ne(SecurityAppBillingBundleStatus.DELETED))
                .orderBy(SECURITY_APP_BILLING_BUNDLE.DISPLAY_ORDER.asc()))
                .map(r -> r.into(AppBillingBundle.class))
                .collectList();
    }
}
