package com.fincity.security.dao.billing;

import static com.fincity.security.jooq.tables.SecurityAppBillingConfig.SECURITY_APP_BILLING_CONFIG;

import java.util.List;

import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.security.dto.billing.AppBillingConfig;
import com.fincity.security.jooq.enums.SecurityAppBillingConfigStatus;
import com.fincity.security.jooq.tables.records.SecurityAppBillingConfigRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class AppBillingConfigDAO extends AbstractUpdatableDAO<SecurityAppBillingConfigRecord, ULong, AppBillingConfig> {

    public AppBillingConfigDAO() {
        super(AppBillingConfig.class, SECURITY_APP_BILLING_CONFIG, SECURITY_APP_BILLING_CONFIG.ID);
    }

    /**
     * Resolve the ACTIVE billing config for an app in a specific client's context.
     * No row means no billing is configured for that client (no enforcement).
     */
    public Mono<AppBillingConfig> findByAppIdAndClientId(ULong appId, ULong clientId) {
        return Mono.from(this.dslContext.selectFrom(SECURITY_APP_BILLING_CONFIG)
                .where(SECURITY_APP_BILLING_CONFIG.APP_ID.eq(appId)
                        .and(SECURITY_APP_BILLING_CONFIG.CLIENT_ID.eq(clientId))
                        .and(SECURITY_APP_BILLING_CONFIG.STATUS.eq(SecurityAppBillingConfigStatus.ACTIVE))))
                .map(r -> r.into(AppBillingConfig.class));
    }

    public Mono<List<AppBillingConfig>> findByApp(ULong appId) {
        return Flux.from(this.dslContext.selectFrom(SECURITY_APP_BILLING_CONFIG)
                .where(SECURITY_APP_BILLING_CONFIG.APP_ID.eq(appId)))
                .map(r -> r.into(AppBillingConfig.class))
                .collectList();
    }
}
