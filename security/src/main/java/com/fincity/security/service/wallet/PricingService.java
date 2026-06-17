package com.fincity.security.service.wallet;

import java.math.BigDecimal;
import java.util.Optional;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.security.dao.billing.ActionCatalogDAO;
import com.fincity.security.dao.billing.AppActionCostDAO;
import com.fincity.security.dto.billing.ActionCatalog;
import com.fincity.security.dto.billing.AppActionCost;
import com.fincity.security.model.billing.ActionClass;
import com.fincity.security.model.billing.ResolvedCost;

import reactor.core.publisher.Mono;

/**
 * Resolves the credit cost and class for a metered action. Per-app cost
 * ({@link AppActionCost}) overrides the platform {@link ActionCatalog} default.
 * Unknown actions resolve to zero cost, METERED.
 *
 * <p>The vendor-cost-to-credits path (per-model LLM token pricing via
 * CreditPricing) is layered on in Phase 4 alongside the AI usage contract.
 */
@Service
public class PricingService {

    private final AppActionCostDAO appActionCostDAO;
    private final ActionCatalogDAO actionCatalogDAO;

    public PricingService(AppActionCostDAO appActionCostDAO, ActionCatalogDAO actionCatalogDAO) {
        this.appActionCostDAO = appActionCostDAO;
        this.actionCatalogDAO = actionCatalogDAO;
    }

    /**
     * Resolve the cost for an action. Rates are owned by the exposing client, so
     * per-action overrides are looked up by (app, urlClient, action); the
     * platform catalog supplies the default when the client has no override.
     */
    public Mono<ResolvedCost> resolveCost(ULong urlClientId, ULong appId, String actionKey, BigDecimal quantity) {

        BigDecimal qty = quantity == null ? BigDecimal.ONE : quantity;

        Mono<Optional<AppActionCost>> perApp = (appId == null || urlClientId == null)
                ? Mono.just(Optional.empty())
                : appActionCostDAO.findByAppClientAndActionKey(appId, urlClientId, actionKey)
                        .map(Optional::of).defaultIfEmpty(Optional.empty());

        Mono<Optional<ActionCatalog>> catalog = actionCatalogDAO.findByActionKey(actionKey)
                .map(Optional::of).defaultIfEmpty(Optional.empty());

        return Mono.zip(perApp, catalog).map(t -> combine(t.getT1(), t.getT2(), qty));
    }

    private ResolvedCost combine(Optional<AppActionCost> perApp, Optional<ActionCatalog> catalog, BigDecimal qty) {

        BigDecimal unit = perApp.map(AppActionCost::getCreditCost)
                .or(() -> catalog.map(ActionCatalog::getDefaultUnitCost))
                .orElse(BigDecimal.ZERO);

        ActionClass cls = perApp.map(AppActionCost::getActionClassOverride)
                .map(o -> ActionClass.valueOf(o.name()))
                .or(() -> catalog.map(c -> ActionClass.valueOf(c.getDefaultActionClass().name())))
                .orElse(ActionClass.METERED);

        return new ResolvedCost(unit.multiply(qty), cls);
    }
}
