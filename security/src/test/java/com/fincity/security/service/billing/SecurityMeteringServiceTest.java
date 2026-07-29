package com.fincity.security.service.billing;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fincity.security.dao.billing.MeteringCountDAO;
import com.fincity.security.model.billing.BillingActionKeys;
import com.fincity.security.model.billing.ChargeRequest;
import com.fincity.security.model.billing.ChargeResult;
import com.fincity.security.model.billing.MeteringInstruction;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for the security-owned metering (app/site/user): a window charges the
 * current head-count per (C, app, M) instruction, and reconciliation back-charges
 * only the windows of a day not already in the ledger.
 */
@ExtendWith(MockitoExtension.class)
class SecurityMeteringServiceTest {

    @Mock
    private AppBillingConfigService configService;
    @Mock
    private WalletService walletService;
    @Mock
    private MeteringCountDAO countDAO;

    private SecurityMeteringService service;

    private static final ULong APP_ID = ULong.valueOf(2);
    private static final ULong M_CLIENT = ULong.valueOf(20);
    private static final ULong C_CLIENT = ULong.valueOf(10);
    private static final LocalDate DAY = LocalDate.of(2026, 6, 15);

    private static final MeteringInstruction INSTR =
            new MeteringInstruction("CCCC", C_CLIENT, "adzump", APP_ID, "MMMM", M_CLIENT);

    @BeforeEach
    void setUp() {
        service = new SecurityMeteringService(configService, walletService, countDAO);
        // Default: no instructions for any action; individual tests override per action.
        lenient().when(configService.chargeInstructions(any())).thenReturn(Flux.empty());
        lenient().when(walletService.charge(any()))
                .thenReturn(Mono.just(new ChargeResult(true, false, false, BigDecimal.ZERO)));
    }

    @Nested
    class Window {

        @Test
        void chargesAppRentWithCurrentCountForTheWindow() {
            when(configService.chargeInstructions(BillingActionKeys.APP_RENT)).thenReturn(Flux.just(INSTR));
            when(countDAO.countAppsOwnedBy(M_CLIENT)).thenReturn(Mono.just(5));

            StepVerifier.create(service.runWindow(DAY, 7)).verifyComplete();

            ArgumentCaptor<ChargeRequest> cap = ArgumentCaptor.forClass(ChargeRequest.class);
            verify(walletService).charge(cap.capture());
            ChargeRequest req = cap.getValue();
            org.junit.jupiter.api.Assertions.assertEquals(BillingActionKeys.APP_RENT, req.actionKey());
            org.junit.jupiter.api.Assertions.assertEquals(M_CLIENT, req.billedClientId());
            org.junit.jupiter.api.Assertions.assertEquals(C_CLIENT, req.configClientId());
            org.junit.jupiter.api.Assertions.assertEquals(BigDecimal.valueOf(5), req.quantity());
            org.junit.jupiter.api.Assertions.assertEquals(7, req.windowIndex());
            org.junit.jupiter.api.Assertions.assertEquals(DAY, req.date());
        }

        @Test
        void zeroCountIsNotCharged() {
            when(configService.chargeInstructions(BillingActionKeys.USER)).thenReturn(Flux.just(INSTR));
            when(countDAO.countUsersWithProfileInApp(M_CLIENT, APP_ID)).thenReturn(Mono.just(0));

            StepVerifier.create(service.runWindow(DAY, 7)).verifyComplete();

            verify(walletService, never()).charge(any());
        }
    }

    @Nested
    class Reconcile {

        @Test
        void backChargesOnlyTheMissingWindows() {
            when(configService.chargeInstructions(BillingActionKeys.APP_RENT)).thenReturn(Flux.just(INSTR));
            // Windows 0..94 already charged; only window 95 is missing.
            List<Short> done = IntStream.range(0, 95).mapToObj(i -> (short) i).toList();
            when(walletService.chargedWindows(M_CLIENT, APP_ID, BillingActionKeys.APP_RENT, DAY))
                    .thenReturn(Mono.just(done));
            when(countDAO.countAppsOwnedBy(M_CLIENT)).thenReturn(Mono.just(5));

            StepVerifier.create(service.reconcileDay(DAY)).verifyComplete();

            ArgumentCaptor<ChargeRequest> cap = ArgumentCaptor.forClass(ChargeRequest.class);
            verify(walletService, times(1)).charge(cap.capture());
            org.junit.jupiter.api.Assertions.assertEquals(95, cap.getValue().windowIndex());
        }

        @Test
        void fullyChargedDayDoesNothing() {
            when(configService.chargeInstructions(BillingActionKeys.APP_RENT)).thenReturn(Flux.just(INSTR));
            List<Short> all = IntStream.range(0, 96).mapToObj(i -> (short) i).toList();
            when(walletService.chargedWindows(M_CLIENT, APP_ID, BillingActionKeys.APP_RENT, DAY))
                    .thenReturn(Mono.just(all));

            StepVerifier.create(service.reconcileDay(DAY)).verifyComplete();

            verify(walletService, never()).charge(any());
        }
    }
}
