package com.fincity.security.service.billing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.billing.MeteringCountDAO;
import com.fincity.security.model.billing.BillingActionKeys;
import com.fincity.security.model.billing.ChargeRequest;
import com.fincity.security.model.billing.MeteringInstruction;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * The security-owned metered actions (app rent, site rent, per-user), run
 * in-process against WalletService. Triggered by the worker every 15 minutes,
 * and reconciled nightly for any window missed to downtime.
 */
@Service
public class SecurityMeteringService {

    private static final String[] SECURITY_ACTIONS = {
            BillingActionKeys.APP_RENT, BillingActionKeys.SITE_RENT, BillingActionKeys.USER };

    private final AppBillingConfigService configService;
    private final WalletService walletService;
    private final MeteringCountDAO countDAO;

    public SecurityMeteringService(AppBillingConfigService configService, WalletService walletService,
            MeteringCountDAO countDAO) {
        this.configService = configService;
        this.walletService = walletService;
        this.countDAO = countDAO;
    }

    /** Charge the current 15-minute window (worker trigger). */
    public Mono<Void> runCurrentWindow() {
        LocalDate date = LocalDate.now();
        int window = LocalTime.now().toSecondOfDay() / 900;
        return this.runWindow(date, window);
    }

    public Mono<Void> runWindow(LocalDate date, int window) {
        return Flux.just(SECURITY_ACTIONS)
                .concatMap(action -> this.configService.chargeInstructions(action)
                        .concatMap(instr -> this.chargeOne(action, instr, date, window)))
                .then()
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "SecurityMeteringService.runWindow"));
    }

    /** Nightly reconcile: back-charge any window of {@code date} not already charged. */
    public Mono<Void> reconcileDay(LocalDate date) {
        return Flux.just(SECURITY_ACTIONS)
                .concatMap(action -> this.configService.chargeInstructions(action)
                        .concatMap(instr -> this.walletService
                                .chargedWindows(instr.billedClientId(), instr.appId(), action, date)
                                .flatMapMany(done -> {
                                    Set<Short> doneSet = new HashSet<>(done);
                                    return Flux.range(0, 96)
                                            .filter(w -> !doneSet.contains((short) w.intValue()))
                                            .concatMap(w -> this.chargeOne(action, instr, date, w));
                                })))
                .then()
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "SecurityMeteringService.reconcileDay"));
    }

    private Mono<Void> chargeOne(String action, MeteringInstruction instr, LocalDate date, int window) {
        return this.countFor(action, instr)
                .filter(count -> count > 0)
                .flatMap(count -> this.walletService.charge(new ChargeRequest(
                        instr.configClientId(), instr.billedClientId(), instr.appId(), action,
                        BigDecimal.valueOf(count), date, window)))
                .onErrorResume(e -> Mono.empty())
                .then();
    }

    private Mono<Integer> countFor(String action, MeteringInstruction instr) {
        return switch (action) {
            case BillingActionKeys.APP_RENT -> this.countDAO.countAppsOwnedBy(instr.billedClientId());
            case BillingActionKeys.SITE_RENT -> this.countDAO.countSitesOwnedBy(instr.billedClientId());
            case BillingActionKeys.USER ->
                this.countDAO.countUsersWithProfileInApp(instr.billedClientId(), instr.appId());
            default -> Mono.just(0);
        };
    }
}
