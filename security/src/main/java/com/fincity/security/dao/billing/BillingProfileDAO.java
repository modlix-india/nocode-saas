package com.fincity.security.dao.billing;

import static com.fincity.security.jooq.Tables.SECURITY_BILLING_PROFILE;

import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.security.dto.billing.BillingProfile;
import com.fincity.security.jooq.tables.records.SecurityBillingProfileRecord;

import reactor.core.publisher.Mono;

@Component
public class BillingProfileDAO extends AbstractUpdatableDAO<SecurityBillingProfileRecord, ULong, BillingProfile> {

    protected BillingProfileDAO() {
        super(BillingProfile.class, SECURITY_BILLING_PROFILE, SECURITY_BILLING_PROFILE.ID);
    }

    /** The buyer client's billing profile for an app (one per client+app). */
    public Mono<BillingProfile> findByClientAndApp(ULong clientId, ULong appId) {
        return Mono.from(this.dslContext.selectFrom(SECURITY_BILLING_PROFILE)
                .where(SECURITY_BILLING_PROFILE.CLIENT_ID.eq(clientId))
                .and(SECURITY_BILLING_PROFILE.APP_ID.eq(appId)))
                .map(r -> r.into(BillingProfile.class));
    }
}
