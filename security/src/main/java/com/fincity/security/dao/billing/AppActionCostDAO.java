package com.fincity.security.dao.billing;

import static com.fincity.security.jooq.tables.SecurityAppActionCost.SECURITY_APP_ACTION_COST;
import static com.fincity.security.jooq.tables.SecurityAppBillingConfig.SECURITY_APP_BILLING_CONFIG;
import static com.fincity.security.jooq.tables.SecurityApp.SECURITY_APP;
import static com.fincity.security.jooq.tables.SecurityClient.SECURITY_CLIENT;

import java.util.List;

import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.commons.security.model.wallet.RentTarget;
import com.fincity.security.dto.billing.AppActionCost;
import com.fincity.security.jooq.enums.SecurityAppActionCostStatus;
import com.fincity.security.jooq.tables.records.SecurityAppActionCostRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class AppActionCostDAO extends AbstractUpdatableDAO<SecurityAppActionCostRecord, ULong, AppActionCost> {

    public AppActionCostDAO() {
        super(AppActionCost.class, SECURITY_APP_ACTION_COST, SECURITY_APP_ACTION_COST.ID);
    }

    /** All action cost rows for a billing config, ordered by action key. */
    public Mono<List<AppActionCost>> findByConfigId(ULong billingConfigId) {
        return Flux.from(this.dslContext.selectFrom(SECURITY_APP_ACTION_COST)
                .where(SECURITY_APP_ACTION_COST.BILLING_CONFIG_ID.eq(billingConfigId))
                .orderBy(SECURITY_APP_ACTION_COST.ACTION_KEY))
                .map(r -> r.into(AppActionCost.class))
                .collectList();
    }

    /** An action's credit cost under a billing config (one config per app+client). */
    public Mono<AppActionCost> findByConfigAndActionKey(ULong billingConfigId, String actionKey) {
        return Mono.from(this.dslContext.selectFrom(SECURITY_APP_ACTION_COST)
                .where(SECURITY_APP_ACTION_COST.BILLING_CONFIG_ID.eq(billingConfigId)
                        .and(SECURITY_APP_ACTION_COST.ACTION_KEY.eq(actionKey))))
                .map(r -> r.into(AppActionCost.class));
    }

    /**
     * Enforced billing configs that carry a cost row for {@code actionKey},
     * with the owning app code and owner client (code + id). Used to enumerate
     * rent targets: core then asks security for each owner's direct managed
     * clients and counts their usage.
     */
    public Mono<List<RentTarget>> findConfigsWithActionCost(String actionKey) {
        return Flux.from(this.dslContext
                .select(SECURITY_APP.APP_CODE, SECURITY_CLIENT.CODE)
                .from(SECURITY_APP_ACTION_COST)
                .join(SECURITY_APP_BILLING_CONFIG)
                .on(SECURITY_APP_BILLING_CONFIG.ID.eq(SECURITY_APP_ACTION_COST.BILLING_CONFIG_ID))
                .join(SECURITY_APP).on(SECURITY_APP.ID.eq(SECURITY_APP_BILLING_CONFIG.APP_ID))
                .join(SECURITY_CLIENT).on(SECURITY_CLIENT.ID.eq(SECURITY_APP_BILLING_CONFIG.CLIENT_ID))
                .where(SECURITY_APP_ACTION_COST.ACTION_KEY.eq(actionKey)
                        .and(SECURITY_APP_ACTION_COST.STATUS.eq(SecurityAppActionCostStatus.ACTIVE))
                        .and(SECURITY_APP_BILLING_CONFIG.ENFORCED.eq((byte) 1))))
                .map(r -> new RentTarget().setAppCode(r.value1()).setOwnerClientCode(r.value2()))
                .collectList();
    }
}
