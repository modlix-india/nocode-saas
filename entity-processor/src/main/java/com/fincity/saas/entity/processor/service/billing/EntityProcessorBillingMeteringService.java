package com.fincity.saas.entity.processor.service.billing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;

import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.TicketDAO;
import com.fincity.saas.entity.processor.feign.IFeignSecurityBillingService;
import com.fincity.saas.entity.processor.model.billing.ChargeRequest;
import com.fincity.saas.entity.processor.model.billing.MeteringInstruction;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Entity-processor's slice of the 15-minute token metering loop: for every
 * (C, app, M) that carries an {@code entityprocessor.deals} rate, count M's active
 * tickets for (appCode, clientCode = M) and post the raw count to security, which
 * does all pricing. Reconciliation re-posts every window of a day idempotently.
 */
@Service
public class EntityProcessorBillingMeteringService {

    private static final String ACTION_DEALS = "entityprocessor.deals";

    private final IFeignSecurityBillingService securityBilling;
    private final TicketDAO ticketDAO;

    public EntityProcessorBillingMeteringService(IFeignSecurityBillingService securityBilling, TicketDAO ticketDAO) {
        this.securityBilling = securityBilling;
        this.ticketDAO = ticketDAO;
    }

    /** Worker trigger: charge the current 15-minute window. */
    public Mono<Boolean> meterCurrentWindow() {
        LocalDate date = LocalDate.now();
        int window = LocalTime.now().toSecondOfDay() / 900;
        return this.run(date, List.of(window))
                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                        "EntityProcessorBillingMeteringService.meterCurrentWindow"));
    }

    /**
     * Worker trigger: reconcile a day (default yesterday) by re-posting every window;
     * security no-ops the windows already charged, filling only the genuinely missed.
     */
    public Mono<Boolean> reconcile(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now().minusDays(1) : date;
        List<Integer> windows = IntStream.range(0, 96).boxed().toList();
        return this.run(day, windows)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "EntityProcessorBillingMeteringService.reconcile"));
    }

    private Mono<Boolean> run(LocalDate date, List<Integer> windows) {
        return this.securityBilling.getInstructions(ACTION_DEALS)
                .concatMap(instr -> this.ticketDAO.countActiveTickets(instr.appCode(), instr.billedClientCode())
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
                        ACTION_DEALS, qty, date, w))
                .toList();
    }
}
