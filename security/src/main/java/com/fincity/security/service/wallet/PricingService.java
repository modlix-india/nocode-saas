package com.fincity.security.service.wallet;

import java.math.BigDecimal;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.security.dao.billing.AppActionCostDAO;
import com.fincity.security.dto.billing.AppActionCost;
import com.fincity.security.model.billing.ActionClass;
import com.fincity.security.model.billing.ResolvedCost;

import reactor.core.publisher.Mono;

/**
 * Resolves the credit cost and class for a metered action. Costs are config:
 * each {@link AppActionCost} row hangs off a billing config (one per app+client),
 * so the caller passes the resolved billing-config id. An action with no row
 * under that config is free (zero cost, METERED). There is no global catalog.
 *
 * <p>The vendor-cost-to-credits path (per-model LLM token pricing via
 * CreditPricing) is layered on in Phase 4 alongside the AI usage contract.
 */
@Service
public class PricingService {

    private final AppActionCostDAO appActionCostDAO;

    public PricingService(AppActionCostDAO appActionCostDAO) {
        this.appActionCostDAO = appActionCostDAO;
    }

    /**
     * Resolve the cost for an action under a billing config. No config or no cost
     * row for the action means free (zero, METERED).
     */
    public Mono<ResolvedCost> resolveCost(ULong billingConfigId, String actionKey, BigDecimal quantity) {

        BigDecimal qty = quantity == null ? BigDecimal.ONE : quantity;

        if (billingConfigId == null)
            return Mono.just(new ResolvedCost(BigDecimal.ZERO, ActionClass.METERED));

        return appActionCostDAO.findByConfigAndActionKey(billingConfigId, actionKey)
                .map(cost -> toResolved(cost, qty))
                .defaultIfEmpty(new ResolvedCost(BigDecimal.ZERO, ActionClass.METERED));
    }

    private ResolvedCost toResolved(AppActionCost cost, BigDecimal qty) {
        BigDecimal unit = cost.getCreditCost() == null ? BigDecimal.ZERO : cost.getCreditCost();
        ActionClass cls = cost.getActionClass() == null
                ? ActionClass.METERED
                : ActionClass.valueOf(cost.getActionClass().name());
        return new ResolvedCost(unit.multiply(qty), cls);
    }
}
