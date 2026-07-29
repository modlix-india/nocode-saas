package com.fincity.saas.entity.processor.service.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fincity.saas.entity.processor.dao.TicketDAO;
import com.fincity.saas.entity.processor.feign.IFeignSecurityBillingService;
import com.fincity.saas.entity.processor.model.billing.ChargeRequest;
import com.fincity.saas.entity.processor.model.billing.MeteringInstruction;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Entity-processor metering: per (C, app, M) instruction with a deals rate, count
 * active tickets for (app, M) and post the raw count to security. A window posts
 * one charge; reconciliation posts all 96; a zero count posts nothing.
 */
@ExtendWith(MockitoExtension.class)
class EntityProcessorBillingMeteringServiceTest {

    @Mock
    private IFeignSecurityBillingService securityBilling;
    @Mock
    private TicketDAO ticketDAO;

    private EntityProcessorBillingMeteringService service;

    private static final ULong APP_ID = ULong.valueOf(2);
    private static final ULong C_CLIENT = ULong.valueOf(10);
    private static final ULong M_CLIENT = ULong.valueOf(20);
    private static final String ACTION = "entityprocessor.deals";

    private static final MeteringInstruction INSTR =
            new MeteringInstruction("CCCC", C_CLIENT, "leadzump", APP_ID, "MMMM", M_CLIENT);

    @BeforeEach
    void setUp() {
        service = new EntityProcessorBillingMeteringService(securityBilling, ticketDAO);
        lenient().when(securityBilling.charge(any())).thenReturn(Mono.just(true));
    }

    @SuppressWarnings("unchecked")
    private List<ChargeRequest> captureCharges() {
        ArgumentCaptor<List<ChargeRequest>> cap = ArgumentCaptor.forClass(List.class);
        verify(securityBilling).charge(cap.capture());
        return cap.getValue();
    }

    @Test
    void windowPostsOneChargeWithTheDealCount() {
        when(securityBilling.getInstructions(ACTION)).thenReturn(Flux.just(INSTR));
        when(ticketDAO.countActiveTickets("leadzump", "MMMM")).thenReturn(Mono.just(42L));

        assertEquals(Boolean.TRUE, service.meterCurrentWindow().block());

        List<ChargeRequest> charges = captureCharges();
        assertEquals(1, charges.size());
        ChargeRequest req = charges.get(0);
        assertEquals(ACTION, req.actionKey());
        assertEquals(M_CLIENT, req.billedClientId());
        assertEquals(BigDecimal.valueOf(42), req.quantity());
    }

    @Test
    void reconcilePostsAllNinetySixWindows() {
        when(securityBilling.getInstructions(ACTION)).thenReturn(Flux.just(INSTR));
        when(ticketDAO.countActiveTickets("leadzump", "MMMM")).thenReturn(Mono.just(42L));

        assertEquals(Boolean.TRUE, service.reconcile(java.time.LocalDate.of(2026, 6, 15)).block());

        assertEquals(96, captureCharges().size());
    }

    @Test
    void zeroCountPostsNothing() {
        when(securityBilling.getInstructions(ACTION)).thenReturn(Flux.just(INSTR));
        when(ticketDAO.countActiveTickets("leadzump", "MMMM")).thenReturn(Mono.just(0L));

        assertEquals(Boolean.TRUE, service.meterCurrentWindow().block());

        verify(securityBilling, never()).charge(any());
    }
}
