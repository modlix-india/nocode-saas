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

    /** Per-(app, exposing-client) override of an action's credit cost. */
    public Mono<AppActionCost> findByAppClientAndActionKey(ULong appId, ULong clientId, String actionKey) {
        return Mono.from(this.dslContext.selectFrom(SECURITY_APP_ACTION_COST)
                .where(SECURITY_APP_ACTION_COST.APP_ID.eq(appId)
                        .and(SECURITY_APP_ACTION_COST.CLIENT_ID.eq(clientId))
                        .and(SECURITY_APP_ACTION_COST.ACTION_KEY.eq(actionKey))))
                .map(r -> r.into(AppActionCost.class));
    }
}
