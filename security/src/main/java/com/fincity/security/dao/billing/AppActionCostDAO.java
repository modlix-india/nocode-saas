package com.fincity.security.dao.billing;

import static com.fincity.security.jooq.tables.SecurityAppActionCost.SECURITY_APP_ACTION_COST;

import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.security.dto.billing.AppActionCost;
import com.fincity.security.jooq.tables.records.SecurityAppActionCostRecord;

import reactor.core.publisher.Mono;

@Component
public class AppActionCostDAO extends AbstractUpdatableDAO<SecurityAppActionCostRecord, ULong, AppActionCost> {

    public AppActionCostDAO() {
        super(AppActionCost.class, SECURITY_APP_ACTION_COST, SECURITY_APP_ACTION_COST.ID);
    }

    /** An action's credit cost under a billing config (one config per app+client). */
    public Mono<AppActionCost> findByConfigAndActionKey(ULong billingConfigId, String actionKey) {
        return Mono.from(this.dslContext.selectFrom(SECURITY_APP_ACTION_COST)
                .where(SECURITY_APP_ACTION_COST.BILLING_CONFIG_ID.eq(billingConfigId)
                        .and(SECURITY_APP_ACTION_COST.ACTION_KEY.eq(actionKey))))
                .map(r -> r.into(AppActionCost.class));
    }
}
