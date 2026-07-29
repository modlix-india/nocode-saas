package com.fincity.saas.core.service.billing;

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

import com.fincity.saas.commons.core.service.connection.appdata.AppDataService;
import com.fincity.saas.core.feign.IFeignSecurityBillingService;
import com.fincity.saas.core.model.billing.ChargeRequest;
import com.fincity.saas.core.model.billing.MeteringInstruction;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Core metering: per (C, app, M) instruction with a storage-rows rate, count M's
 * estimated rows and post the raw count to security. A window posts one charge;
 * reconciliation posts all 96 (idempotent server-side); a zero count posts nothing.
 */
@ExtendWith(MockitoExtension.class)
class CoreBillingMeteringServiceTest {

    @Mock
    private IFeignSecurityBillingService securityBilling;
    @Mock
    private AppDataService appDataService;

    private CoreBillingMeteringService service;

    private static final ULong APP_ID = ULong.valueOf(2);
    private static final ULong C_CLIENT = ULong.valueOf(10);
    private static final ULong M_CLIENT = ULong.valueOf(20);
    private static final String ACTION = "core.storage.rows";

    private static final MeteringInstruction INSTR =
            new MeteringInstruction("CCCC", C_CLIENT, "adzump", APP_ID, "MMMM", M_CLIENT);

    @BeforeEach
    void setUp() {
        service = new CoreBillingMeteringService(securityBilling, appDataService);
        lenient().when(securityBilling.charge(any())).thenReturn(Mono.just(true));
    }

    @SuppressWarnings("unchecked")
    private List<ChargeRequest> captureCharges() {
        ArgumentCaptor<List<ChargeRequest>> cap = ArgumentCaptor.forClass(List.class);
        verify(securityBilling).charge(cap.capture());
        return cap.getValue();
    }

    @Test
    void windowPostsOneChargeWithTheEstimatedCount() {
        when(securityBilling.getInstructions(ACTION)).thenReturn(Flux.just(INSTR));
        when(appDataService.estimatedRowCount("adzump", "MMMM")).thenReturn(Mono.just(5L));

        assertEquals(Boolean.TRUE, service.meterCurrentWindow().block());

        List<ChargeRequest> charges = captureCharges();
        assertEquals(1, charges.size());
        ChargeRequest req = charges.get(0);
        assertEquals(ACTION, req.actionKey());
        assertEquals(M_CLIENT, req.billedClientId());
        assertEquals(C_CLIENT, req.configClientId());
        assertEquals(BigDecimal.valueOf(5), req.quantity());
    }

    @Test
    void reconcilePostsAllNinetySixWindows() {
        when(securityBilling.getInstructions(ACTION)).thenReturn(Flux.just(INSTR));
        when(appDataService.estimatedRowCount("adzump", "MMMM")).thenReturn(Mono.just(5L));

        assertEquals(Boolean.TRUE, service.reconcile(java.time.LocalDate.of(2026, 6, 15)).block());

        List<ChargeRequest> charges = captureCharges();
        assertEquals(96, charges.size());
    }

    @Test
    void zeroCountPostsNothing() {
        when(securityBilling.getInstructions(ACTION)).thenReturn(Flux.just(INSTR));
        when(appDataService.estimatedRowCount("adzump", "MMMM")).thenReturn(Mono.just(0L));

        assertEquals(Boolean.TRUE, service.meterCurrentWindow().block());

        verify(securityBilling, never()).charge(any());
    }
}
