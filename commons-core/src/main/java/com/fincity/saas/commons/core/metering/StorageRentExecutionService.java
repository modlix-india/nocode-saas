package com.fincity.saas.commons.core.metering;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.fincity.saas.commons.core.service.connection.appdata.MongoAppDataService;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.model.wallet.RentTarget;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Hourly storage-rent drip. Worker triggers this; it asks security for the apps
 * whose billing config carries a {@code core.storage.row} cost, expands each
 * owner to its DIRECT managed clients (one hop), counts each client's stored
 * rows, and asks security to drip the rent. All billing math (rate, idempotent
 * per-hour drip, suspend-at-floor) stays in security; this only counts.
 */
@Service
public class StorageRentExecutionService {

    private static final String ACTION_KEY = "core.storage.row";

    private final IFeignSecurityService securityService;
    private final MongoAppDataService mongoAppDataService;

    public StorageRentExecutionService(IFeignSecurityService securityService,
            MongoAppDataService mongoAppDataService) {
        this.securityService = securityService;
        this.mongoAppDataService = mongoAppDataService;
    }

    /** Drip storage rent for every billed app; returns total rows charged. */
    public Mono<Long> dripStorageRent() {
        return this.securityService.rentTargets(ACTION_KEY)
                .flatMapMany(Flux::fromIterable)
                .flatMap(this::dripForTarget)
                .reduce(0L, Long::sum);
    }

    private Mono<Long> dripForTarget(RentTarget target) {
        return this.securityService.getClientByCode(target.getOwnerClientCode())
                .flatMap(owner -> this.securityService.getClientIdsOfManager(owner.getId()))
                .flatMapMany(Flux::fromIterable)
                .flatMap(this.securityService::getClientById)
                .flatMap(managed -> dripForClient(target.getAppCode(), managed.getCode()))
                .reduce(0L, Long::sum);
    }

    private Mono<Long> dripForClient(String appCode, String clientCode) {
        return this.mongoAppDataService.countAllRows(clientCode, appCode)
                .flatMap(rows -> rows <= 0
                        ? Mono.just(0L)
                        : this.securityService
                                .chargeRent(appCode, clientCode, ACTION_KEY, BigDecimal.valueOf(rows))
                                .thenReturn(rows));
    }
}
