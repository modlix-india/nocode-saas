package com.fincity.security.service.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fincity.security.dao.billing.AppBillingConfigDAO;
import com.fincity.security.dao.wallet.WalletDAO;
import com.fincity.security.dao.wallet.WalletTransactionDAO;
import com.fincity.security.dto.billing.AppBillingConfig;
import com.fincity.security.dto.wallet.Wallet;
import com.fincity.security.dto.wallet.WalletTransaction;
import com.fincity.security.jooq.enums.SecuritySoxLogObjectName;
import com.fincity.security.jooq.enums.SecurityWalletStatus;
import com.fincity.security.jooq.enums.SecurityWalletTransactionReferenceType;
import com.fincity.security.jooq.enums.SecurityWalletTransactionTransactionType;
import com.fincity.security.model.billing.ActionClass;
import com.fincity.security.model.billing.ChargeOutcome;
import com.fincity.security.model.billing.ChargeRequest;
import com.fincity.security.model.billing.ResolvedCost;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for {@link WalletService}, the heart of the token-wallet model.
 *
 * <p>The tests are grouped by the role each method plays in the six billing
 * scenarios:
 * <ul>
 *   <li><b>consolidatedDebit</b> drips rent and consolidated usage onto the
 *       consumer's wallet (scenarios 1-5) and flips it to SUSPENDED at the floor
 *       (scenario 6, allow-negative-then-suspend).</li>
 *   <li><b>charge / reserve</b> are the synchronous metered paths
 *       (METERED blocks, ENGAGEMENT gets grace).</li>
 *   <li><b>isCreationAllowed / resolveStatus</b> are the two gates (creation 402
 *       and serving swap) of scenario 6.</li>
 *   <li><b>topUp / grant / adjust / burnSeatFee</b> are the credit/seat ops.</li>
 * </ul>
 * Throughout: no (app, urlClient) config means not-enforced (free); a config
 * that exists but is not enforced records a shadow row without moving the
 * balance; idempotency keys make every debit a safe replay.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WalletServiceTest {

    @Mock private WalletDAO walletDAO;
    @Mock private WalletTransactionDAO walletTransactionDAO;
    @Mock private LedgerService ledgerService;
    @Mock private PricingService pricingService;
    @Mock private AppBillingConfigDAO appBillingConfigDAO;

    private WalletService service;

    private static final ULong CLIENT_ID = ULong.valueOf(100);
    private static final ULong URL_CLIENT_ID = ULong.valueOf(1);
    private static final ULong APP_ID = ULong.valueOf(50);
    private static final ULong CONFIG_ID = ULong.valueOf(7);
    private static final ULong WALLET_ID = ULong.valueOf(900);
    private static final ULong TXN_ID = ULong.valueOf(5000);
    private static final ULong RES_ID = ULong.valueOf(6000);
    private static final String ACTION = "core.email.send";
    private static final String IDEM = "usage:100:50:core.email.send:111";

    @BeforeEach
    void setUp() {
        service = new WalletService(walletDAO, walletTransactionDAO, ledgerService, pricingService,
                appBillingConfigDAO);

        // Ledger records succeed and hand back a transaction with an id.
        when(ledgerService.record(any(WalletTransaction.class))).thenAnswer(inv -> {
            WalletTransaction t = inv.getArgument(0);
            t.setId(TXN_ID);
            return Mono.just(t);
        });
        // getOrCreateWallet provisioning path stamps an id on the new parent wallet.
        when(walletDAO.create(any(Wallet.class))).thenAnswer(inv -> {
            Wallet w = inv.getArgument(0);
            w.setId(WALLET_ID);
            return Mono.just(w);
        });
    }

    private Wallet wallet(BigDecimal balance, SecurityWalletStatus status, BigDecimal floor) {
        Wallet w = new Wallet().setClientId(CLIENT_ID).setBalance(balance)
                .setReservedBalance(BigDecimal.ZERO).setStatus(status).setGraceFloor(floor);
        w.setId(WALLET_ID);
        return w;
    }

    private AppBillingConfig config(boolean enforced) {
        AppBillingConfig c = new AppBillingConfig().setAppId(APP_ID).setClientId(URL_CLIENT_ID)
                .setEnforced(enforced);
        c.setId(CONFIG_ID);
        return c;
    }

    private ChargeRequest chargeReq(BigDecimal qty, String idem) {
        return new ChargeRequest().setClientId(CLIENT_ID).setUrlClientId(URL_CLIENT_ID).setAppId(APP_ID)
                .setActionKey(ACTION).setQuantity(qty).setIdempotencyKey(idem);
    }

    // ===== getOrCreateWallet / resolveStatus =====

    @Test
    void getOrCreateWallet_existing_returnsResolved() {
        Wallet existing = wallet(BigDecimal.TEN, SecurityWalletStatus.ACTIVE, BigDecimal.ZERO);
        when(walletDAO.resolveWallet(CLIENT_ID, null)).thenReturn(Mono.just(existing));

        StepVerifier.create(service.getOrCreateWallet(CLIENT_ID))
                .assertNext(w -> assertEquals(WALLET_ID, w.getId()))
                .verifyComplete();
        verify(walletDAO, never()).create(any());
    }

    @Test
    void getOrCreateWallet_missing_provisionsActiveZeroParent() {
        when(walletDAO.resolveWallet(CLIENT_ID, null)).thenReturn(Mono.empty());

        StepVerifier.create(service.getOrCreateWallet(CLIENT_ID))
                .assertNext(w -> {
                    assertEquals(CLIENT_ID, w.getClientId());
                    assertEquals(0, w.getBalance().signum());
                    assertEquals(SecurityWalletStatus.ACTIVE, w.getStatus());
                })
                .verifyComplete();
        verify(walletDAO).create(any(Wallet.class));
    }

    @Test
    void resolveStatus_returnsWalletStatus() {
        when(walletDAO.resolveWallet(CLIENT_ID, APP_ID))
                .thenReturn(Mono.just(wallet(BigDecimal.ZERO, SecurityWalletStatus.SUSPENDED, BigDecimal.ZERO)));

        StepVerifier.create(service.resolveStatus(CLIENT_ID, APP_ID))
                .expectNext(SecurityWalletStatus.SUSPENDED)
                .verifyComplete();
    }

    @Test
    void resolveStatus_noWallet_defaultsActive() {
        when(walletDAO.resolveWallet(CLIENT_ID, APP_ID)).thenReturn(Mono.empty());

        StepVerifier.create(service.resolveStatus(CLIENT_ID, APP_ID))
                .expectNext(SecurityWalletStatus.ACTIVE)
                .verifyComplete();
    }

    // ===== charge (synchronous metered path) =====

    @Test
    void charge_noConfig_notEnforced() {
        when(appBillingConfigDAO.findByAppIdAndClientId(APP_ID, URL_CLIENT_ID)).thenReturn(Mono.empty());

        StepVerifier.create(service.charge(chargeReq(BigDecimal.ONE, null)))
                .assertNext(r -> {
                    assertTrue(r.allowed());
                    assertEquals(ChargeOutcome.NOT_ENFORCED, r.outcome());
                })
                .verifyComplete();
        verify(walletDAO, never()).atomicDebit(any(), any());
    }

    @Test
    void charge_configNotEnforced_recordsShadowWithoutMovingBalance() {
        when(appBillingConfigDAO.findByAppIdAndClientId(APP_ID, URL_CLIENT_ID))
                .thenReturn(Mono.just(config(false)));
        when(walletDAO.resolveWallet(CLIENT_ID, APP_ID))
                .thenReturn(Mono.just(wallet(BigDecimal.TEN, SecurityWalletStatus.ACTIVE, BigDecimal.ZERO)));
        when(walletTransactionDAO.findByIdempotencyKey(any(), anyString())).thenReturn(Mono.empty());
        when(pricingService.resolveCost(eq(CONFIG_ID), eq(ACTION), any()))
                .thenReturn(Mono.just(new ResolvedCost(new BigDecimal("4"), ActionClass.METERED)));

        StepVerifier.create(service.charge(chargeReq(BigDecimal.ONE, "k1")))
                .assertNext(r -> assertEquals(ChargeOutcome.SHADOW_RECORDED, r.outcome()))
                .verifyComplete();

        verify(walletDAO, never()).atomicDebit(any(), any());
        verify(ledgerService).record(any(WalletTransaction.class));
    }

    @Test
    void charge_meteredSufficient_charged() {
        enforcedSetup(new BigDecimal("3"), ActionClass.METERED);
        when(walletDAO.atomicDebit(WALLET_ID, new BigDecimal("3"))).thenReturn(Mono.just(1));
        when(walletDAO.readById(WALLET_ID))
                .thenReturn(Mono.just(wallet(new BigDecimal("7"), SecurityWalletStatus.ACTIVE, BigDecimal.ZERO)));

        StepVerifier.create(service.charge(chargeReq(BigDecimal.ONE, "k2")))
                .assertNext(r -> {
                    assertTrue(r.allowed());
                    assertEquals(ChargeOutcome.CHARGED, r.outcome());
                })
                .verifyComplete();
    }

    @Test
    void charge_meteredInsufficient_blocked() {
        enforcedSetup(new BigDecimal("999"), ActionClass.METERED);
        when(walletDAO.atomicDebit(WALLET_ID, new BigDecimal("999"))).thenReturn(Mono.just(0));
        when(walletDAO.readById(WALLET_ID))
                .thenReturn(Mono.just(wallet(new BigDecimal("5"), SecurityWalletStatus.ACTIVE, BigDecimal.ZERO)));

        StepVerifier.create(service.charge(chargeReq(BigDecimal.ONE, "k3")))
                .assertNext(r -> {
                    assertFalse(r.allowed());
                    assertEquals(ChargeOutcome.BLOCKED_INSUFFICIENT, r.outcome());
                })
                .verifyComplete();
        verify(ledgerService, never()).record(any());
    }

    @Test
    void charge_engagementBeyondZero_grantsGrace() {
        enforcedSetup(new BigDecimal("3"), ActionClass.ENGAGEMENT);
        when(walletDAO.atomicDebitAllowNegative(WALLET_ID, new BigDecimal("3"))).thenReturn(Mono.just(1));
        when(walletDAO.readById(WALLET_ID))
                .thenReturn(Mono.just(wallet(new BigDecimal("-2"), SecurityWalletStatus.ACTIVE,
                        new BigDecimal("-100"))));

        StepVerifier.create(service.charge(chargeReq(BigDecimal.ONE, "k4")))
                .assertNext(r -> {
                    assertTrue(r.allowed());
                    assertEquals(ChargeOutcome.GRACE_ALLOWED, r.outcome());
                })
                .verifyComplete();
    }

    @Test
    void charge_zeroCost_chargedZeroWithoutDebit() {
        enforcedSetup(BigDecimal.ZERO, ActionClass.METERED);

        StepVerifier.create(service.charge(chargeReq(BigDecimal.ONE, "k5")))
                .assertNext(r -> {
                    assertEquals(ChargeOutcome.CHARGED, r.outcome());
                    assertEquals(0, r.creditsCharged().signum());
                })
                .verifyComplete();
        verify(walletDAO, never()).atomicDebit(any(), any());
    }

    @Test
    void charge_idempotentReplay_returnsPriorTransaction() {
        when(appBillingConfigDAO.findByAppIdAndClientId(APP_ID, URL_CLIENT_ID))
                .thenReturn(Mono.just(config(true)));
        when(walletDAO.resolveWallet(CLIENT_ID, APP_ID))
                .thenReturn(Mono.just(wallet(BigDecimal.TEN, SecurityWalletStatus.ACTIVE, BigDecimal.ZERO)));
        WalletTransaction prior = new WalletTransaction().setCredits(new BigDecimal("3"))
                .setBalanceAfter(new BigDecimal("7"));
        prior.setId(TXN_ID);
        when(walletTransactionDAO.findByIdempotencyKey(WALLET_ID, "dupe")).thenReturn(Mono.just(prior));

        StepVerifier.create(service.charge(chargeReq(BigDecimal.ONE, "dupe")))
                .assertNext(r -> assertEquals(ChargeOutcome.IDEMPOTENT_REPLAY, r.outcome()))
                .verifyComplete();
        verify(pricingService, never()).resolveCost(any(), any(), any());
        verify(walletDAO, never()).atomicDebit(any(), any());
    }

    private void enforcedSetup(BigDecimal credits, ActionClass cls) {
        when(appBillingConfigDAO.findByAppIdAndClientId(APP_ID, URL_CLIENT_ID))
                .thenReturn(Mono.just(config(true)));
        when(walletDAO.resolveWallet(CLIENT_ID, APP_ID))
                .thenReturn(Mono.just(wallet(BigDecimal.TEN, SecurityWalletStatus.ACTIVE, BigDecimal.ZERO)));
        when(walletTransactionDAO.findByIdempotencyKey(any(), anyString())).thenReturn(Mono.empty());
        when(pricingService.resolveCost(eq(CONFIG_ID), eq(ACTION), any()))
                .thenReturn(Mono.just(new ResolvedCost(credits, cls)));
    }

    // ===== consolidatedDebit (rent + 15-min usage; allow-negative then suspend) =====

    @Test
    void consolidatedDebit_noConfig_notEnforced() {
        when(appBillingConfigDAO.findByAppIdAndClientId(APP_ID, URL_CLIENT_ID)).thenReturn(Mono.empty());

        StepVerifier.create(service.consolidatedDebit(CLIENT_ID, URL_CLIENT_ID, APP_ID, ACTION,
                BigDecimal.ONE, IDEM))
                .assertNext(r -> assertEquals(ChargeOutcome.NOT_ENFORCED, r.outcome()))
                .verifyComplete();
        verify(walletDAO, never()).atomicDebitUnconditional(any(), any());
    }

    @Test
    void consolidatedDebit_configNotEnforced_shadowOnly() {
        consolidatedSetup(false, new BigDecimal("4"),
                wallet(BigDecimal.TEN, SecurityWalletStatus.ACTIVE, BigDecimal.ZERO));

        StepVerifier.create(service.consolidatedDebit(CLIENT_ID, URL_CLIENT_ID, APP_ID, ACTION,
                BigDecimal.ONE, IDEM))
                .assertNext(r -> assertEquals(ChargeOutcome.SHADOW_RECORDED, r.outcome()))
                .verifyComplete();
        verify(walletDAO, never()).atomicDebitUnconditional(any(), any());
        verify(walletDAO, never()).setStatus(any(), any());
    }

    @Test
    void consolidatedDebit_enforcedPositiveBalance_chargedNoSuspend() {
        consolidatedSetup(true, new BigDecimal("3"), null);
        when(walletDAO.atomicDebitUnconditional(WALLET_ID, new BigDecimal("3"))).thenReturn(Mono.just(1));
        when(walletDAO.readById(WALLET_ID))
                .thenReturn(Mono.just(wallet(new BigDecimal("7"), SecurityWalletStatus.ACTIVE, BigDecimal.ZERO)));

        StepVerifier.create(service.consolidatedDebit(CLIENT_ID, URL_CLIENT_ID, APP_ID, ACTION,
                BigDecimal.valueOf(3), IDEM))
                .assertNext(r -> assertEquals(ChargeOutcome.CHARGED, r.outcome()))
                .verifyComplete();
        verify(walletDAO).atomicDebitUnconditional(WALLET_ID, new BigDecimal("3"));
        verify(walletDAO, never()).setStatus(any(), any());
    }

    @Test
    void consolidatedDebit_balanceCrossesFloor_flipsToSuspended() {
        consolidatedSetup(true, new BigDecimal("12"), null);
        when(walletDAO.atomicDebitUnconditional(WALLET_ID, new BigDecimal("12"))).thenReturn(Mono.just(1));
        // After the unconditional debit the balance is below the grace floor.
        when(walletDAO.readById(WALLET_ID))
                .thenReturn(Mono.just(wallet(new BigDecimal("-2"), SecurityWalletStatus.ACTIVE, BigDecimal.ZERO)));
        when(walletDAO.setStatus(WALLET_ID, SecurityWalletStatus.SUSPENDED)).thenReturn(Mono.just(1));

        StepVerifier.create(service.consolidatedDebit(CLIENT_ID, URL_CLIENT_ID, APP_ID, ACTION,
                BigDecimal.valueOf(12), IDEM))
                .assertNext(r -> assertEquals(ChargeOutcome.CHARGED, r.outcome()))
                .verifyComplete();
        verify(walletDAO).setStatus(WALLET_ID, SecurityWalletStatus.SUSPENDED);
    }

    @Test
    void consolidatedDebit_alreadySuspended_doesNotReSuspend() {
        consolidatedSetup(true, new BigDecimal("5"), null);
        when(walletDAO.atomicDebitUnconditional(WALLET_ID, new BigDecimal("5"))).thenReturn(Mono.just(1));
        when(walletDAO.readById(WALLET_ID))
                .thenReturn(Mono.just(wallet(new BigDecimal("-9"), SecurityWalletStatus.SUSPENDED, BigDecimal.ZERO)));

        StepVerifier.create(service.consolidatedDebit(CLIENT_ID, URL_CLIENT_ID, APP_ID, ACTION,
                BigDecimal.valueOf(5), IDEM))
                .assertNext(r -> assertEquals(ChargeOutcome.CHARGED, r.outcome()))
                .verifyComplete();
        verify(walletDAO, never()).setStatus(any(), any());
    }

    @Test
    void consolidatedDebit_zeroCost_chargedZero() {
        consolidatedSetup(true, BigDecimal.ZERO, null);
        when(walletDAO.resolveWallet(CLIENT_ID, APP_ID))
                .thenReturn(Mono.just(wallet(BigDecimal.TEN, SecurityWalletStatus.ACTIVE, BigDecimal.ZERO)));

        StepVerifier.create(service.consolidatedDebit(CLIENT_ID, URL_CLIENT_ID, APP_ID, ACTION,
                BigDecimal.ZERO, IDEM))
                .assertNext(r -> {
                    assertEquals(ChargeOutcome.CHARGED, r.outcome());
                    assertEquals(0, r.creditsCharged().signum());
                })
                .verifyComplete();
        verify(walletDAO, never()).atomicDebitUnconditional(any(), any());
    }

    @Test
    void consolidatedDebit_idempotentReplay_skipsDebit() {
        when(appBillingConfigDAO.findByAppIdAndClientId(APP_ID, URL_CLIENT_ID))
                .thenReturn(Mono.just(config(true)));
        when(walletDAO.resolveWallet(CLIENT_ID, APP_ID))
                .thenReturn(Mono.just(wallet(BigDecimal.TEN, SecurityWalletStatus.ACTIVE, BigDecimal.ZERO)));
        WalletTransaction prior = new WalletTransaction().setCredits(new BigDecimal("3"))
                .setBalanceAfter(new BigDecimal("7"));
        prior.setId(TXN_ID);
        when(walletTransactionDAO.findByIdempotencyKey(WALLET_ID, IDEM)).thenReturn(Mono.just(prior));

        StepVerifier.create(service.consolidatedDebit(CLIENT_ID, URL_CLIENT_ID, APP_ID, ACTION,
                BigDecimal.ONE, IDEM))
                .assertNext(r -> assertEquals(ChargeOutcome.IDEMPOTENT_REPLAY, r.outcome()))
                .verifyComplete();
        verify(walletDAO, never()).atomicDebitUnconditional(any(), any());
    }

    private void consolidatedSetup(boolean enforced, BigDecimal credits, Wallet resolved) {
        when(appBillingConfigDAO.findByAppIdAndClientId(APP_ID, URL_CLIENT_ID))
                .thenReturn(Mono.just(config(enforced)));
        when(walletDAO.resolveWallet(CLIENT_ID, APP_ID)).thenReturn(Mono.just(
                resolved != null ? resolved : wallet(BigDecimal.TEN, SecurityWalletStatus.ACTIVE, BigDecimal.ZERO)));
        when(walletTransactionDAO.findByIdempotencyKey(any(), anyString())).thenReturn(Mono.empty());
        when(pricingService.resolveCost(eq(CONFIG_ID), eq(ACTION), any()))
                .thenReturn(Mono.just(new ResolvedCost(credits, ActionClass.METERED)));
    }

    // ===== isCreationAllowed (creation gate, scenario 6) =====

    @Test
    void isCreationAllowed_noConfig_allowed() {
        when(appBillingConfigDAO.findByAppIdAndClientId(APP_ID, URL_CLIENT_ID)).thenReturn(Mono.empty());

        StepVerifier.create(service.isCreationAllowed(CLIENT_ID, APP_ID, URL_CLIENT_ID))
                .expectNext(Boolean.TRUE).verifyComplete();
    }

    @Test
    void isCreationAllowed_configNotEnforced_allowed() {
        when(appBillingConfigDAO.findByAppIdAndClientId(APP_ID, URL_CLIENT_ID))
                .thenReturn(Mono.just(config(false)));

        StepVerifier.create(service.isCreationAllowed(CLIENT_ID, APP_ID, URL_CLIENT_ID))
                .expectNext(Boolean.TRUE).verifyComplete();
        verify(walletDAO, never()).resolveWallet(any(), any());
    }

    @Test
    void isCreationAllowed_enforcedActiveWallet_allowed() {
        when(appBillingConfigDAO.findByAppIdAndClientId(APP_ID, URL_CLIENT_ID))
                .thenReturn(Mono.just(config(true)));
        when(walletDAO.resolveWallet(CLIENT_ID, APP_ID))
                .thenReturn(Mono.just(wallet(BigDecimal.ONE, SecurityWalletStatus.ACTIVE, BigDecimal.ZERO)));

        StepVerifier.create(service.isCreationAllowed(CLIENT_ID, APP_ID, URL_CLIENT_ID))
                .expectNext(Boolean.TRUE).verifyComplete();
    }

    @Test
    void isCreationAllowed_enforcedSuspendedWallet_blocked() {
        when(appBillingConfigDAO.findByAppIdAndClientId(APP_ID, URL_CLIENT_ID))
                .thenReturn(Mono.just(config(true)));
        when(walletDAO.resolveWallet(CLIENT_ID, APP_ID))
                .thenReturn(Mono.just(wallet(BigDecimal.ZERO, SecurityWalletStatus.SUSPENDED, BigDecimal.ZERO)));

        StepVerifier.create(service.isCreationAllowed(CLIENT_ID, APP_ID, URL_CLIENT_ID))
                .expectNext(Boolean.FALSE).verifyComplete();
    }

    @Test
    void isCreationAllowed_enforcedNoWalletYet_allowed() {
        when(appBillingConfigDAO.findByAppIdAndClientId(APP_ID, URL_CLIENT_ID))
                .thenReturn(Mono.just(config(true)));
        when(walletDAO.resolveWallet(CLIENT_ID, APP_ID)).thenReturn(Mono.empty());

        StepVerifier.create(service.isCreationAllowed(CLIENT_ID, APP_ID, URL_CLIENT_ID))
                .expectNext(Boolean.TRUE).verifyComplete();
    }

    // ===== reserve =====

    @Test
    void reserve_noConfig_reservedZero() {
        when(appBillingConfigDAO.findByAppIdAndClientId(APP_ID, URL_CLIENT_ID)).thenReturn(Mono.empty());

        StepVerifier.create(service.reserve(chargeReq(BigDecimal.ONE, null)))
                .assertNext(r -> {
                    assertTrue(r.allowed());
                    assertEquals(0, r.creditsReserved().signum());
                })
                .verifyComplete();
    }

    @Test
    void reserve_configNotEnforced_reservedZero() {
        when(appBillingConfigDAO.findByAppIdAndClientId(APP_ID, URL_CLIENT_ID))
                .thenReturn(Mono.just(config(false)));

        StepVerifier.create(service.reserve(chargeReq(BigDecimal.ONE, null)))
                .assertNext(r -> assertTrue(r.allowed()))
                .verifyComplete();
        verify(walletDAO, never()).atomicReserve(any(), any());
    }

    @Test
    void reserve_enforcedSufficient_holdsCredits() {
        when(appBillingConfigDAO.findByAppIdAndClientId(APP_ID, URL_CLIENT_ID))
                .thenReturn(Mono.just(config(true)));
        when(walletDAO.resolveWallet(CLIENT_ID, APP_ID))
                .thenReturn(Mono.just(wallet(BigDecimal.TEN, SecurityWalletStatus.ACTIVE, BigDecimal.ZERO)));
        when(pricingService.resolveCost(eq(CONFIG_ID), eq(ACTION), any()))
                .thenReturn(Mono.just(new ResolvedCost(new BigDecimal("4"), ActionClass.METERED)));
        when(walletDAO.atomicReserve(WALLET_ID, new BigDecimal("4"))).thenReturn(Mono.just(1));
        when(walletDAO.readById(WALLET_ID))
                .thenReturn(Mono.just(wallet(BigDecimal.TEN, SecurityWalletStatus.ACTIVE, BigDecimal.ZERO)));

        StepVerifier.create(service.reserve(chargeReq(BigDecimal.ONE, "r1")))
                .assertNext(r -> {
                    assertTrue(r.allowed());
                    assertEquals(TXN_ID, r.reservationId());
                })
                .verifyComplete();
    }

    @Test
    void reserve_enforcedInsufficient_blocked() {
        when(appBillingConfigDAO.findByAppIdAndClientId(APP_ID, URL_CLIENT_ID))
                .thenReturn(Mono.just(config(true)));
        when(walletDAO.resolveWallet(CLIENT_ID, APP_ID))
                .thenReturn(Mono.just(wallet(BigDecimal.ONE, SecurityWalletStatus.ACTIVE, BigDecimal.ZERO)));
        when(pricingService.resolveCost(eq(CONFIG_ID), eq(ACTION), any()))
                .thenReturn(Mono.just(new ResolvedCost(new BigDecimal("99"), ActionClass.METERED)));
        when(walletDAO.atomicReserve(WALLET_ID, new BigDecimal("99"))).thenReturn(Mono.just(0));
        when(walletDAO.readById(WALLET_ID))
                .thenReturn(Mono.just(wallet(BigDecimal.ONE, SecurityWalletStatus.ACTIVE, BigDecimal.ZERO)));

        StepVerifier.create(service.reserve(chargeReq(BigDecimal.ONE, "r2")))
                .assertNext(r -> {
                    assertFalse(r.allowed());
                    assertEquals(ChargeOutcome.BLOCKED_INSUFFICIENT, r.outcome());
                })
                .verifyComplete();
    }

    // ===== settle / release =====

    @Test
    void settle_consumesActualCredits() {
        WalletTransaction reservation = new WalletTransaction().setWalletId(WALLET_ID)
                .setCredits(new BigDecimal("10")).setActionKey(ACTION).setAppId(APP_ID);
        reservation.setId(RES_ID);
        when(walletTransactionDAO.readById(RES_ID)).thenReturn(Mono.just(reservation));
        when(walletTransactionDAO.findByIdempotencyKey(any(), anyString())).thenReturn(Mono.empty());
        when(walletDAO.atomicSettle(WALLET_ID, new BigDecimal("10"), new BigDecimal("6")))
                .thenReturn(Mono.just(1));
        when(walletDAO.readById(WALLET_ID))
                .thenReturn(Mono.just(wallet(new BigDecimal("4"), SecurityWalletStatus.ACTIVE, BigDecimal.ZERO)));

        StepVerifier.create(service.settle(RES_ID, new BigDecimal("6"), "s1"))
                .assertNext(r -> {
                    assertEquals(ChargeOutcome.CHARGED, r.outcome());
                    assertEquals(0, new BigDecimal("6").compareTo(r.creditsCharged()));
                })
                .verifyComplete();
        verify(walletDAO).atomicSettle(WALLET_ID, new BigDecimal("10"), new BigDecimal("6"));
    }

    @Test
    void release_returnsFullReservation() {
        WalletTransaction reservation = new WalletTransaction().setWalletId(WALLET_ID)
                .setCredits(new BigDecimal("10")).setActionKey(ACTION).setAppId(APP_ID);
        reservation.setId(RES_ID);
        when(walletTransactionDAO.readById(RES_ID)).thenReturn(Mono.just(reservation));
        when(walletTransactionDAO.findByIdempotencyKey(any(), anyString())).thenReturn(Mono.empty());
        when(walletDAO.atomicRelease(WALLET_ID, new BigDecimal("10"))).thenReturn(Mono.just(1));
        when(walletDAO.readById(WALLET_ID))
                .thenReturn(Mono.just(wallet(BigDecimal.TEN, SecurityWalletStatus.ACTIVE, BigDecimal.ZERO)));

        StepVerifier.create(service.release(RES_ID, "rel1"))
                .assertNext(r -> assertEquals(0, new BigDecimal("10").compareTo(r.creditsCharged())))
                .verifyComplete();
        verify(walletDAO).atomicRelease(WALLET_ID, new BigDecimal("10"));
    }

    // ===== topUp / grant / adjust / burnSeatFee =====

    @Test
    void topUp_newKey_creditsWallet() {
        when(walletDAO.atomicCredit(WALLET_ID, new BigDecimal("500"))).thenReturn(Mono.just(1));
        when(walletDAO.readById(WALLET_ID))
                .thenReturn(Mono.just(wallet(new BigDecimal("500"), SecurityWalletStatus.ACTIVE, BigDecimal.ZERO)));
        when(walletTransactionDAO.findByIdempotencyKey(WALLET_ID, "pay-1")).thenReturn(Mono.empty());

        StepVerifier.create(service.topUp(WALLET_ID, new BigDecimal("500"), "pay-1",
                SecurityWalletTransactionReferenceType.PAYMENT, "PAY1"))
                .assertNext(w -> assertEquals(0, new BigDecimal("500").compareTo(w.getBalance())))
                .verifyComplete();
        verify(walletDAO).atomicCredit(WALLET_ID, new BigDecimal("500"));
    }

    @Test
    void topUp_duplicateKey_doesNotDoubleCredit() {
        WalletTransaction prior = new WalletTransaction().setWalletId(WALLET_ID).setCredits(new BigDecimal("500"));
        prior.setId(TXN_ID);
        when(walletTransactionDAO.findByIdempotencyKey(WALLET_ID, "pay-1")).thenReturn(Mono.just(prior));
        when(walletDAO.readById(WALLET_ID))
                .thenReturn(Mono.just(wallet(new BigDecimal("500"), SecurityWalletStatus.ACTIVE, BigDecimal.ZERO)));
        // creditWith assembles the credit chain eagerly; stub so it builds, but the
        // duplicate-key branch never subscribes it, so no ledger row is written.
        when(walletDAO.atomicCredit(WALLET_ID, new BigDecimal("500"))).thenReturn(Mono.just(1));

        StepVerifier.create(service.topUp(WALLET_ID, new BigDecimal("500"), "pay-1",
                SecurityWalletTransactionReferenceType.PAYMENT, "PAY1"))
                .assertNext(w -> assertEquals(WALLET_ID, w.getId()))
                .verifyComplete();
        // The replay path returns the current wallet without recording a credit.
        verify(ledgerService, never()).record(any());
    }

    @Test
    void grant_creditsWalletAsGrant() {
        when(walletDAO.atomicCredit(WALLET_ID, new BigDecimal("100"))).thenReturn(Mono.just(1));
        when(walletDAO.readById(WALLET_ID))
                .thenReturn(Mono.just(wallet(new BigDecimal("100"), SecurityWalletStatus.ACTIVE, BigDecimal.ZERO)));
        when(walletTransactionDAO.findByIdempotencyKey(WALLET_ID, "grant-jan")).thenReturn(Mono.empty());

        StepVerifier.create(service.grant(WALLET_ID, new BigDecimal("100"), "grant-jan"))
                .assertNext(w -> assertEquals(0, new BigDecimal("100").compareTo(w.getBalance())))
                .verifyComplete();
    }

    @Test
    void adjust_increase_usesCredit() {
        when(walletDAO.atomicCredit(WALLET_ID, new BigDecimal("25"))).thenReturn(Mono.just(1));
        when(walletDAO.readById(WALLET_ID))
                .thenReturn(Mono.just(wallet(new BigDecimal("25"), SecurityWalletStatus.ACTIVE, BigDecimal.ZERO)));

        StepVerifier.create(service.adjust(WALLET_ID, new BigDecimal("25"), true, "manual credit"))
                .assertNext(w -> assertEquals(0, new BigDecimal("25").compareTo(w.getBalance())))
                .verifyComplete();
        verify(walletDAO).atomicCredit(WALLET_ID, new BigDecimal("25"));
        verify(walletDAO, never()).atomicDebitAllowNegative(any(), any());
    }

    @Test
    void adjust_decrease_usesAllowNegativeDebit() {
        when(walletDAO.atomicDebitAllowNegative(WALLET_ID, new BigDecimal("25"))).thenReturn(Mono.just(1));
        when(walletDAO.readById(WALLET_ID))
                .thenReturn(Mono.just(wallet(new BigDecimal("-5"), SecurityWalletStatus.ACTIVE, BigDecimal.ZERO)));

        StepVerifier.create(service.adjust(WALLET_ID, new BigDecimal("25"), false, "manual debit"))
                .assertNext(w -> assertEquals(0, new BigDecimal("-5").compareTo(w.getBalance())))
                .verifyComplete();
        verify(walletDAO).atomicDebitAllowNegative(WALLET_ID, new BigDecimal("25"));
    }

    @Test
    void burnSeatFee_sufficient_charged() {
        when(walletDAO.resolveWallet(CLIENT_ID, null))
                .thenReturn(Mono.just(wallet(BigDecimal.TEN, SecurityWalletStatus.ACTIVE, BigDecimal.ZERO)));
        when(walletTransactionDAO.findByIdempotencyKey(any(), anyString())).thenReturn(Mono.empty());
        when(walletDAO.atomicDebit(WALLET_ID, new BigDecimal("2"))).thenReturn(Mono.just(1));
        when(walletDAO.readById(WALLET_ID))
                .thenReturn(Mono.just(wallet(new BigDecimal("8"), SecurityWalletStatus.ACTIVE, BigDecimal.ZERO)));

        StepVerifier.create(service.burnSeatFee(CLIENT_ID, APP_ID, new BigDecimal("2"), "seat:1"))
                .assertNext(r -> assertEquals(ChargeOutcome.CHARGED, r.outcome()))
                .verifyComplete();
    }

    @Test
    void burnSeatFee_insufficient_blocked() {
        when(walletDAO.resolveWallet(CLIENT_ID, null))
                .thenReturn(Mono.just(wallet(BigDecimal.ONE, SecurityWalletStatus.ACTIVE, BigDecimal.ZERO)));
        when(walletTransactionDAO.findByIdempotencyKey(any(), anyString())).thenReturn(Mono.empty());
        when(walletDAO.atomicDebit(WALLET_ID, new BigDecimal("50"))).thenReturn(Mono.just(0));
        when(walletDAO.readById(WALLET_ID))
                .thenReturn(Mono.just(wallet(BigDecimal.ONE, SecurityWalletStatus.ACTIVE, BigDecimal.ZERO)));

        StepVerifier.create(service.burnSeatFee(CLIENT_ID, APP_ID, new BigDecimal("50"), "seat:2"))
                .assertNext(r -> assertEquals(ChargeOutcome.BLOCKED_INSUFFICIENT, r.outcome()))
                .verifyComplete();
    }

    @Test
    void burnSeatFee_zero_chargedZero() {
        when(walletDAO.resolveWallet(CLIENT_ID, null))
                .thenReturn(Mono.just(wallet(BigDecimal.TEN, SecurityWalletStatus.ACTIVE, BigDecimal.ZERO)));
        when(walletTransactionDAO.findByIdempotencyKey(any(), anyString())).thenReturn(Mono.empty());

        StepVerifier.create(service.burnSeatFee(CLIENT_ID, APP_ID, BigDecimal.ZERO, "seat:3"))
                .assertNext(r -> assertEquals(0, r.creditsCharged().signum()))
                .verifyComplete();
        verify(walletDAO, never()).atomicDebit(any(), any());
    }

    @Test
    void getBalance_returnsResolvedWallet() {
        when(walletDAO.resolveWallet(CLIENT_ID, null))
                .thenReturn(Mono.just(wallet(new BigDecimal("42"), SecurityWalletStatus.ACTIVE, BigDecimal.ZERO)));

        StepVerifier.create(service.getBalance(CLIENT_ID))
                .assertNext(w -> assertEquals(0, new BigDecimal("42").compareTo(w.getBalance())))
                .verifyComplete();
    }

    @Test
    void grant_nullKey_creditsDirectlyWithoutIdempotencyLookup() {
        when(walletDAO.atomicCredit(WALLET_ID, new BigDecimal("100"))).thenReturn(Mono.just(1));
        when(walletDAO.readById(WALLET_ID))
                .thenReturn(Mono.just(wallet(new BigDecimal("100"), SecurityWalletStatus.ACTIVE, BigDecimal.ZERO)));

        StepVerifier.create(service.grant(WALLET_ID, new BigDecimal("100"), null))
                .assertNext(w -> assertEquals(0, new BigDecimal("100").compareTo(w.getBalance())))
                .verifyComplete();
        verify(walletDAO).atomicCredit(WALLET_ID, new BigDecimal("100"));
        verify(walletTransactionDAO, never()).findByIdempotencyKey(any(), anyString());
    }

    @Test
    void charge_nullKeyWithExplicitReferenceType_charged() {
        when(appBillingConfigDAO.findByAppIdAndClientId(APP_ID, URL_CLIENT_ID))
                .thenReturn(Mono.just(config(true)));
        when(walletDAO.resolveWallet(CLIENT_ID, APP_ID))
                .thenReturn(Mono.just(wallet(BigDecimal.TEN, SecurityWalletStatus.ACTIVE, BigDecimal.ZERO)));
        when(pricingService.resolveCost(eq(CONFIG_ID), eq(ACTION), any()))
                .thenReturn(Mono.just(new ResolvedCost(new BigDecimal("3"), ActionClass.METERED)));
        when(walletDAO.atomicDebit(WALLET_ID, new BigDecimal("3"))).thenReturn(Mono.just(1));
        when(walletDAO.readById(WALLET_ID))
                .thenReturn(Mono.just(wallet(new BigDecimal("7"), SecurityWalletStatus.ACTIVE, BigDecimal.ZERO)));

        ChargeRequest req = chargeReq(BigDecimal.ONE, null)
                .setReferenceType(SecurityWalletTransactionReferenceType.ACTION).setReferenceId("ref-1");

        StepVerifier.create(service.charge(req))
                .assertNext(r -> assertEquals(ChargeOutcome.CHARGED, r.outcome()))
                .verifyComplete();
        // Null idempotency key skips the replay lookup entirely.
        verify(walletTransactionDAO, never()).findByIdempotencyKey(any(), anyString());
    }

    @Test
    void metadataAccessors_areWired() {
        assertEquals(SecuritySoxLogObjectName.WALLET, service.getSoxObjectName());

        Wallet w = wallet(BigDecimal.TEN, SecurityWalletStatus.ACTIVE, BigDecimal.ZERO);
        assertEquals(CLIENT_ID, service.resolveClientId(w));
        assertEquals("client " + CLIENT_ID, service.describeEntity(w));
        assertNull(service.describeEntity(null));
        assertNull(service.describeEntity(new Wallet()));
    }

    @Test
    void getRecentTransactions_returnsLedgerRows() {
        when(walletDAO.resolveWallet(CLIENT_ID, null))
                .thenReturn(Mono.just(wallet(BigDecimal.TEN, SecurityWalletStatus.ACTIVE, BigDecimal.ZERO)));
        WalletTransaction t = new WalletTransaction().setWalletId(WALLET_ID)
                .setTransactionType(SecurityWalletTransactionTransactionType.DEBIT);
        when(walletTransactionDAO.findRecentByWallet(WALLET_ID, 10)).thenReturn(Mono.just(List.of(t)));

        StepVerifier.create(service.getRecentTransactions(CLIENT_ID, 10))
                .assertNext(list -> assertEquals(1, list.size()))
                .verifyComplete();
    }
}
