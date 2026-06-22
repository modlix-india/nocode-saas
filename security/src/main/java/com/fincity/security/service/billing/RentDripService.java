package com.fincity.security.service.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.security.model.wallet.RentTarget;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.AppDAO;
import com.fincity.security.dao.UserDAO;
import com.fincity.security.dao.billing.AppActionCostDAO;
import com.fincity.security.dto.App;
import com.fincity.security.service.AppService;
import com.fincity.security.service.ClientManagerService;
import com.fincity.security.service.ClientService;
import com.fincity.security.service.wallet.WalletService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Hourly seat/app/site rent, entirely inside security (it counts its own user
 * and app tables). For each billing config that carries one of these costs, it
 * expands the owner to its DIRECT managed clients (one hop), counts the metric
 * per client, and drips the rent via {@link WalletService#consolidatedDebit}
 * ({@code monthlyRate / hoursInMonth * count}, idempotent per hour). The worker
 * triggers it; no cross-service calls.
 */
@Service
public class RentDripService {

    private static final String SEAT = "platform.seat";
    private static final String APP_RENT = "security.app.rent";
    private static final String SITE_RENT = "security.site.rent";

    private final AppActionCostDAO appActionCostDAO;
    private final ClientService clientService;
    private final AppService appService;
    private final ClientManagerService clientManagerService;
    private final UserDAO userDAO;
    private final AppDAO appDAO;
    private final WalletService walletService;

    public RentDripService(AppActionCostDAO appActionCostDAO, ClientService clientService, AppService appService,
            ClientManagerService clientManagerService, UserDAO userDAO, AppDAO appDAO, WalletService walletService) {
        this.appActionCostDAO = appActionCostDAO;
        this.clientService = clientService;
        this.appService = appService;
        this.clientManagerService = clientManagerService;
        this.userDAO = userDAO;
        this.appDAO = appDAO;
        this.walletService = walletService;
    }

    /** Drip seat, app and site rent in one pass; returns total units charged. */
    public Mono<Long> dripInternalRent() {
        return Flux.just(SEAT, APP_RENT, SITE_RENT)
                .concatMap(this::dripAction)
                .reduce(0L, Long::sum)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "RentDripService.dripInternalRent"));
    }

    private Mono<Long> dripAction(String actionKey) {
        return this.appActionCostDAO.findConfigsWithActionCost(actionKey)
                .flatMapMany(Flux::fromIterable)
                .flatMap(config -> dripForConfig(actionKey, config))
                .reduce(0L, Long::sum);
    }

    private Mono<Long> dripForConfig(String actionKey, RentTarget config) {
        return Mono.zip(
                this.clientService.getClientId(config.getOwnerClientCode()),
                this.appService.getAppByCode(config.getAppCode()).map(App::getId))
                .flatMap(t -> this.clientManagerService.getClientIdsOfManagerInternal(t.getT1())
                        .flatMapMany(Flux::fromIterable)
                        .flatMap(managedId -> dripForClient(actionKey, t.getT2(), t.getT1(), managedId))
                        .reduce(0L, Long::sum));
    }

    private Mono<Long> dripForClient(String actionKey, ULong appId, ULong ownerId, ULong managedId) {
        return countMetric(actionKey, managedId)
                .flatMap(count -> {
                    if (count <= 0L)
                        return Mono.just(0L);
                    long hoursInMonth = (long) YearMonth.now().lengthOfMonth() * 24;
                    long hourBucket = Instant.now().getEpochSecond() / 3600;
                    BigDecimal quantity = BigDecimal.valueOf(count)
                            .divide(BigDecimal.valueOf(hoursInMonth), 10, RoundingMode.HALF_UP);
                    String idem = "rent:" + actionKey + ":" + appId + ":" + managedId + ":" + hourBucket;
                    return this.walletService
                            .consolidatedDebit(managedId, ownerId, appId, actionKey, quantity, idem)
                            .thenReturn(count);
                });
    }

    private Mono<Long> countMetric(String actionKey, ULong clientId) {
        // Seats = active users of the client; app/site rent = apps owned by the client.
        return SEAT.equals(actionKey)
                ? this.userDAO.countActiveByClient(clientId)
                : this.appDAO.countByClientId(clientId);
    }
}
