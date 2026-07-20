package com.fincity.saas.core.service.billing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;

import com.fincity.saas.commons.core.service.connection.appdata.AppDataService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.core.feign.IFeignSecurityBillingService;
import com.fincity.saas.core.model.billing.ChargeRequest;
import com.fincity.saas.core.model.billing.MeteringInstruction;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Core's slice of the 15-minute token metering loop: for every (C, app, M) that
 * carries a {@code core.storage.rows} rate, count M's estimated rows in its
 * {@code {Mclient}_{appCode}} Mongo DB and post the raw count to security, which
 * does all pricing. Reconciliation re-posts every window of a day idempotently.
 */
@Service
public class CoreBillingMeteringService {

    private static final String ACTION_STORAGE_ROWS = "core.storage.rows";

    private final IFeignSecurityBillingService securityBilling;
    private final AppDataService appDataService;

    public CoreBillingMeteringService(IFeignSecurityBillingService securityBilling,
            AppDataService appDataService) {
        this.securityBilling = securityBilling;
        this.appDataService = appDataService;
    }

    /** Worker trigger: charge the current 15-minute window. */
    public Mono<Boolean> meterCurrentWindow() {
        LocalDate date = LocalDate.now();
        int window = LocalTime.now().toSecondOfDay() / 900;
        return this.run(date, List.of(window))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CoreBillingMeteringService.meterCurrentWindow"));
    }

    /**
     * Worker trigger: reconcile a day (default yesterday) by re-posting every window;
     * security no-ops the windows already charged, filling only the genuinely missed.
     */
    public Mono<Boolean> reconcile(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now().minusDays(1) : date;
        List<Integer> windows = IntStream.range(0, 96).boxed().toList();
        return this.run(day, windows)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CoreBillingMeteringService.reconcile"));
    }

    private Mono<Boolean> run(LocalDate date, List<Integer> windows) {
        return this.securityBilling.getInstructions(ACTION_STORAGE_ROWS)
                .concatMap(instr -> this.countRows(instr)
                        .filter(count -> count > 0)
                        .flatMap(count -> this.securityBilling.charge(this.chargesFor(instr, count, date, windows)))
                        .onErrorResume(e -> Mono.empty()))
                .then(Mono.just(Boolean.TRUE));
    }

    private List<ChargeRequest> chargesFor(MeteringInstruction instr, long count, LocalDate date,
            List<Integer> windows) {
        BigDecimal qty = BigDecimal.valueOf(count);
        return windows.stream()
                .map(w -> new ChargeRequest(instr.configClientId(), instr.billedClientId(), instr.appId(),
                        ACTION_STORAGE_ROWS, qty, date, w))
                .toList();
    }

    private Mono<Long> countRows(MeteringInstruction instr) {
        return this.appDataService.estimatedRowCount(instr.appCode(), instr.billedClientCode());
    }
}
