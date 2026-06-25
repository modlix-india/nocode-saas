package com.fincity.security.service.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.commons.mq.events.EventNames;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.security.dao.billing.WalletDAO;
import com.fincity.security.dao.billing.WalletTransactionDAO;
import com.fincity.security.dto.App;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.billing.AppBillingConfig;
import com.fincity.security.dto.billing.Invoice;
import com.fincity.security.dto.billing.Wallet;
import com.fincity.security.jooq.enums.SecurityAppAppType;
import com.fincity.security.jooq.enums.SecurityClientStatusCode;
import com.fincity.security.jooq.enums.SecurityWalletStatus;
import com.fincity.security.model.billing.AiChargeRequest;
import com.fincity.security.model.billing.BillingActionKeys;
import com.fincity.security.model.billing.ChargeRequest;
import com.fincity.security.service.AbstractServiceUnitTest;
import com.fincity.security.service.AppService;
import com.fincity.security.service.ClientHierarchyService;
import com.fincity.security.service.ClientService;
import com.fincity.security.service.SecurityMessageResourceService;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for the wallet engine: pricing (free quota + proration + floor),
 * ledger-first idempotency, edge-triggered low-balance alerts, zero-crossing
 * suspension, lazy seeding, credit/reactivate, immediate AI charging, the AI gate
 * and the app/site hosting decision.
 */
@ExtendWith(MockitoExtension.class)
class WalletServiceTest extends AbstractServiceUnitTest {

    @Mock
    private WalletDAO walletDAO;
    @Mock
    private WalletTransactionDAO txnDAO;
    @Mock
    private AppBillingConfigService configService;
    @Mock
    private AppService appService;
    @Mock
    private ClientService clientService;
    @Mock
    private ClientHierarchyService clientHierarchyService;
    @Mock
    private CacheService cacheService;
    @Mock
    private EventCreationService ecService;
    @Mock
    private SecurityMessageResourceService messageResourceService;

    private WalletService service;

    private static final ULong APP_ID = ULong.valueOf(2);
    private static final ULong BUILDER_APP_ID = ULong.valueOf(1);
    private static final ULong SITEZUMP_APP_ID = ULong.valueOf(3);
    private static final ULong M_CLIENT = ULong.valueOf(20);
    private static final ULong C_CLIENT = ULong.valueOf(10);
    private static final ULong WALLET_ID = ULong.valueOf(500);
    // June 2026 has 30 days -> windowsInMonth = 30 * 96 = 2880.
    private static final LocalDate DAY = LocalDate.of(2026, 6, 15);
    private static final int WINDOWS_IN_JUNE = 2880;

    @BeforeEach
    void setUp() {
        service = new WalletService(txnDAO, configService, appService, clientService,
                clientHierarchyService, cacheService, ecService, messageResourceService);
        injectDao(service, walletDAO);
        setupCacheService(cacheService);
        lenient().when(ecService.createEvent(any(EventQueObject.class))).thenReturn(Mono.just(true));
    }

    /** Walk the superclass chain to set the protected {@code dao} field. */
    private static void injectDao(Object target, Object dao) {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                var f = c.getDeclaredField("dao");
                f.setAccessible(true);
                f.set(target, dao);
                return;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to inject dao", e);
            }
        }
        throw new IllegalStateException("dao field not found");
    }

    // ---------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------

    private AppBillingConfig config() {
        return new AppBillingConfig().setAppId(APP_ID).setClientId(C_CLIENT);
    }

    private Wallet wallet(BigDecimal balance, SecurityWalletStatus status, byte notified) {
        Wallet w = new Wallet().setClientId(M_CLIENT).setAppId(APP_ID).setBalance(balance)
                .setStatus(status).setLowBalanceNotified(notified);
        w.setId(WALLET_ID);
        return w;
    }

    private ChargeRequest req(BigDecimal quantity, String actionKey) {
        return new ChargeRequest(C_CLIENT, M_CLIENT, APP_ID, actionKey, quantity, DAY, 5);
    }

    private void stubActiveWallet(Wallet w) {
        when(walletDAO.findByClientAndApp(M_CLIENT, APP_ID)).thenReturn(Mono.just(w));
    }

    // ---------------------------------------------------------------------
    // Pricing
    // ---------------------------------------------------------------------

    @Nested
    class Pricing {

        @Test
        void chargesFlooredProratedTokensAboveFreeQuota() {
            // rate=2880/mo, free=3, qty=5 -> billable 2 -> 2*2880/2880 = 2 tokens.
            AppBillingConfig cfg = config()
                    .setUserTokensPerMonth(BigDecimal.valueOf(WINDOWS_IN_JUNE))
                    .setFreeUsers(BigDecimal.valueOf(3));
            when(configService.readByAppAndClientId(APP_ID, C_CLIENT)).thenReturn(Mono.just(cfg));
            stubActiveWallet(wallet(BigDecimal.valueOf(100), SecurityWalletStatus.ACTIVE, (byte) 0));
            when(txnDAO.recordTxn(any())).thenReturn(Mono.just(true));
            when(walletDAO.debitActive(eq(WALLET_ID), eq(BigDecimal.valueOf(2)))).thenReturn(Mono.just(1));

            StepVerifier.create(service.charge(req(BigDecimal.valueOf(5), BillingActionKeys.USER)))
                    .assertNext(r -> {
                        assertTrue(r.charged());
                        assertFalse(r.suspended());
                        assertEquals(BigDecimal.valueOf(98), r.balanceAfter());
                    })
                    .verifyComplete();

            verify(walletDAO).debitActive(WALLET_ID, BigDecimal.valueOf(2));
        }

        @Test
        void floorsToZeroAndDoesNotChargeOrDebit() {
            // rate=100/mo over 2880 windows, qty=1 -> 100/2880 = 0.03 -> floor 0 -> no charge.
            AppBillingConfig cfg = config().setUserTokensPerMonth(BigDecimal.valueOf(100));
            when(configService.readByAppAndClientId(APP_ID, C_CLIENT)).thenReturn(Mono.just(cfg));

            StepVerifier.create(service.charge(req(BigDecimal.ONE, BillingActionKeys.USER)))
                    .assertNext(r -> assertFalse(r.charged()))
                    .verifyComplete();

            verify(walletDAO, never()).debitActive(any(), any());
        }

        @Test
        void noRateMeansNoCharge() {
            when(configService.readByAppAndClientId(APP_ID, C_CLIENT)).thenReturn(Mono.just(config()));

            StepVerifier.create(service.charge(req(BigDecimal.valueOf(100), BillingActionKeys.USER)))
                    .assertNext(r -> assertFalse(r.charged()))
                    .verifyComplete();

            verify(walletDAO, never()).debitActive(any(), any());
        }

        @Test
        void noConfigMeansNoCharge() {
            when(configService.readByAppAndClientId(APP_ID, C_CLIENT)).thenReturn(Mono.empty());

            StepVerifier.create(service.charge(req(BigDecimal.valueOf(100), BillingActionKeys.USER)))
                    .assertNext(r -> assertFalse(r.charged()))
                    .verifyComplete();
        }
    }

    // ---------------------------------------------------------------------
    // Idempotency
    // ---------------------------------------------------------------------

    @Nested
    class Idempotency {

        @Test
        void duplicateWindowDoesNotDoubleDebit() {
            AppBillingConfig cfg = config().setUserTokensPerMonth(BigDecimal.valueOf(WINDOWS_IN_JUNE));
            when(configService.readByAppAndClientId(APP_ID, C_CLIENT)).thenReturn(Mono.just(cfg));
            stubActiveWallet(wallet(BigDecimal.valueOf(100), SecurityWalletStatus.ACTIVE, (byte) 0));
            // Ledger insert is ignored on duplicate -> recordTxn returns false.
            when(txnDAO.recordTxn(any())).thenReturn(Mono.just(false));

            StepVerifier.create(service.charge(req(BigDecimal.valueOf(5), BillingActionKeys.USER)))
                    .assertNext(r -> assertFalse(r.charged()))
                    .verifyComplete();

            verify(walletDAO, never()).debitActive(any(), any());
        }
    }

    // ---------------------------------------------------------------------
    // Alerts + suspension (edge-triggered)
    // ---------------------------------------------------------------------

    @Nested
    class AlertsAndSuspension {

        private void stubCharge(AppBillingConfig cfg, Wallet w, BigDecimal tokens) {
            when(configService.readByAppAndClientId(APP_ID, C_CLIENT)).thenReturn(Mono.just(cfg));
            stubActiveWallet(w);
            when(txnDAO.recordTxn(any())).thenReturn(Mono.just(true));
            when(walletDAO.debitActive(eq(WALLET_ID), eq(tokens))).thenReturn(Mono.just(1));
            lenient().when(walletDAO.setLowBalanceNotified(any(), org.mockito.ArgumentMatchers.anyBoolean()))
                    .thenReturn(Mono.just(1));
            lenient().when(walletDAO.setStatus(any(), any())).thenReturn(Mono.just(1));
            lenient().when(appService.getAppByIdInternal(APP_ID))
                    .thenReturn(Mono.just(TestDataFactory.createOwnApp(APP_ID, C_CLIENT, "adzump")));
            lenient().when(clientService.getClientInfoById(M_CLIENT))
                    .thenReturn(Mono.just(TestDataFactory.createClient(M_CLIENT, "MMMM", "BUS",
                            SecurityClientStatusCode.ACTIVE)));
        }

        @Test
        void firesLowBalanceOnceOnDownwardCrossing() {
            // threshold 400; 420 - 30 -> 390 crosses down; not yet notified.
            AppBillingConfig cfg = config()
                    .setUserTokensPerMonth(BigDecimal.valueOf(WINDOWS_IN_JUNE * 30))
                    .setLowBalanceThreshold(BigDecimal.valueOf(400));
            Wallet w = wallet(BigDecimal.valueOf(420), SecurityWalletStatus.ACTIVE, (byte) 0);
            stubCharge(cfg, w, BigDecimal.valueOf(30));

            StepVerifier.create(service.charge(req(BigDecimal.ONE, BillingActionKeys.USER)))
                    .assertNext(r -> {
                        assertTrue(r.charged());
                        assertTrue(r.lowBalanceCrossed());
                    })
                    .verifyComplete();

            verify(walletDAO).setLowBalanceNotified(WALLET_ID, true);
            verify(ecService, times(1)).createEvent(argEvent(EventNames.WALLET_LOW_BALANCE));
        }

        @Test
        void doesNotFireWhenAlreadyNotified() {
            AppBillingConfig cfg = config()
                    .setUserTokensPerMonth(BigDecimal.valueOf(WINDOWS_IN_JUNE * 30))
                    .setLowBalanceThreshold(BigDecimal.valueOf(400));
            Wallet w = wallet(BigDecimal.valueOf(420), SecurityWalletStatus.ACTIVE, (byte) 1);
            stubCharge(cfg, w, BigDecimal.valueOf(30));

            StepVerifier.create(service.charge(req(BigDecimal.ONE, BillingActionKeys.USER)))
                    .assertNext(r -> assertFalse(r.lowBalanceCrossed()))
                    .verifyComplete();

            verify(ecService, never()).createEvent(argEvent(EventNames.WALLET_LOW_BALANCE));
        }

        @Test
        void doesNotFireWhenAlreadyBelowThreshold() {
            // 390 -> 360: before is already below 400, so no downward crossing.
            AppBillingConfig cfg = config()
                    .setUserTokensPerMonth(BigDecimal.valueOf(WINDOWS_IN_JUNE * 30))
                    .setLowBalanceThreshold(BigDecimal.valueOf(400));
            Wallet w = wallet(BigDecimal.valueOf(390), SecurityWalletStatus.ACTIVE, (byte) 0);
            stubCharge(cfg, w, BigDecimal.valueOf(30));

            StepVerifier.create(service.charge(req(BigDecimal.ONE, BillingActionKeys.USER)))
                    .assertNext(r -> assertFalse(r.lowBalanceCrossed()))
                    .verifyComplete();

            verify(ecService, never()).createEvent(argEvent(EventNames.WALLET_LOW_BALANCE));
        }

        @Test
        void suspendsWhenBalanceCrossesZero() {
            // 10 - 30 -> -20: debit applies (gated on ACTIVE), then wallet suspends.
            AppBillingConfig cfg = config().setUserTokensPerMonth(BigDecimal.valueOf(WINDOWS_IN_JUNE * 30));
            Wallet w = wallet(BigDecimal.valueOf(10), SecurityWalletStatus.ACTIVE, (byte) 0);
            stubCharge(cfg, w, BigDecimal.valueOf(30));

            StepVerifier.create(service.charge(req(BigDecimal.ONE, BillingActionKeys.USER)))
                    .assertNext(r -> {
                        assertTrue(r.charged());
                        assertTrue(r.suspended());
                        assertEquals(BigDecimal.valueOf(-20), r.balanceAfter());
                    })
                    .verifyComplete();

            verify(walletDAO).setStatus(WALLET_ID, SecurityWalletStatus.SUSPENDED);
            verify(ecService).createEvent(argEvent(EventNames.WALLET_SUSPENDED));
        }

        @Test
        void alreadySuspendedWalletIsNotDebited() {
            AppBillingConfig cfg = config().setUserTokensPerMonth(BigDecimal.valueOf(WINDOWS_IN_JUNE));
            when(configService.readByAppAndClientId(APP_ID, C_CLIENT)).thenReturn(Mono.just(cfg));
            stubActiveWallet(wallet(BigDecimal.valueOf(-5), SecurityWalletStatus.SUSPENDED, (byte) 0));
            when(txnDAO.recordTxn(any())).thenReturn(Mono.just(true));

            StepVerifier.create(service.charge(req(BigDecimal.valueOf(5), BillingActionKeys.USER)))
                    .assertNext(r -> {
                        assertFalse(r.charged());
                        assertTrue(r.suspended());
                    })
                    .verifyComplete();

            verify(walletDAO, never()).debitActive(any(), any());
        }
    }

    // ---------------------------------------------------------------------
    // Lazy seed + balance
    // ---------------------------------------------------------------------

    @Nested
    class Lifecycle {

        @Test
        void getOrCreateWalletSeedsOneTokenWhenAbsent() {
            when(walletDAO.findByClientAndApp(M_CLIENT, APP_ID)).thenReturn(Mono.empty());
            Wallet seeded = wallet(BigDecimal.ONE, SecurityWalletStatus.ACTIVE, (byte) 0);
            when(walletDAO.createSeeded(M_CLIENT, APP_ID, BigDecimal.ONE)).thenReturn(Mono.just(seeded));

            StepVerifier.create(service.getOrCreateWallet(M_CLIENT, APP_ID))
                    .assertNext(w -> assertEquals(BigDecimal.ONE, w.getBalance()))
                    .verifyComplete();

            verify(walletDAO).createSeeded(M_CLIENT, APP_ID, BigDecimal.ONE);
        }

        @Test
        void creditFromPaymentReactivatesSuspendedWallet() {
            Invoice invoice = new Invoice().setClientId(M_CLIENT).setAppId(APP_ID)
                    .setTokensPurchased(BigDecimal.valueOf(100)).setPaymentReference("pay_1")
                    .setInvoiceNumber("INV/1");
            stubActiveWallet(wallet(BigDecimal.valueOf(-5), SecurityWalletStatus.SUSPENDED, (byte) 1));
            when(txnDAO.recordTxn(any())).thenReturn(Mono.just(true));
            when(walletDAO.creditBalance(WALLET_ID, BigDecimal.valueOf(100))).thenReturn(Mono.just(1));
            when(walletDAO.setLowBalanceNotified(WALLET_ID, false)).thenReturn(Mono.just(1));
            when(walletDAO.setStatus(WALLET_ID, SecurityWalletStatus.ACTIVE)).thenReturn(Mono.just(1));

            StepVerifier.create(service.creditFromPayment(invoice))
                    .assertNext(r -> {
                        assertTrue(r.charged());
                        assertEquals(BigDecimal.valueOf(95), r.balanceAfter());
                    })
                    .verifyComplete();

            verify(walletDAO).setStatus(WALLET_ID, SecurityWalletStatus.ACTIVE);
            verify(walletDAO).setLowBalanceNotified(WALLET_ID, false);
        }
    }

    // ---------------------------------------------------------------------
    // AI
    // ---------------------------------------------------------------------

    @Nested
    class Ai {

        @Test
        void chargeAiPricesWeightedTokensPerMillion() {
            App builder = TestDataFactory.createOwnApp(BUILDER_APP_ID, ULong.valueOf(1), "appbuilder");
            Client m = TestDataFactory.createClient(M_CLIENT, "MMMM", "BUS", SecurityClientStatusCode.ACTIVE);
            AppBillingConfig cfg = new AppBillingConfig().setAppId(BUILDER_APP_ID).setClientId(C_CLIENT)
                    .setAiTokensPerMillion(BigDecimal.valueOf(1000));

            when(appService.getAppByCode("appbuilder")).thenReturn(Mono.just(builder));
            when(clientService.getClientBy("MMMM")).thenReturn(Mono.just(m));
            when(clientHierarchyService.getManagingClientIds(M_CLIENT)).thenReturn(Mono.just(List.of(C_CLIENT)));
            when(configService.readByAppAndClientId(BUILDER_APP_ID, C_CLIENT)).thenReturn(Mono.just(cfg));
            Wallet w = new Wallet().setClientId(M_CLIENT).setAppId(BUILDER_APP_ID)
                    .setBalance(BigDecimal.valueOf(5000)).setStatus(SecurityWalletStatus.ACTIVE)
                    .setLowBalanceNotified((byte) 0);
            w.setId(WALLET_ID);
            when(walletDAO.findByClientAndApp(M_CLIENT, BUILDER_APP_ID)).thenReturn(Mono.just(w));
            when(txnDAO.recordTxn(any())).thenReturn(Mono.just(true));
            // 2,000,000 weighted * 1000 / 1,000,000 = 2000 tokens.
            when(walletDAO.debitActive(WALLET_ID, BigDecimal.valueOf(2000))).thenReturn(Mono.just(1));

            AiChargeRequest req = new AiChargeRequest("MMMM", "appbuilder", "opus",
                    BigDecimal.valueOf(2_000_000), "req-1", "sess-1");

            StepVerifier.create(service.chargeAi(req))
                    .assertNext(r -> assertTrue(r.charged()))
                    .verifyComplete();

            verify(walletDAO).debitActive(WALLET_ID, BigDecimal.valueOf(2000));
        }

        @Test
        void aiAllowedFalseWhenBuilderWalletSuspended() {
            App builder = TestDataFactory.createOwnApp(BUILDER_APP_ID, ULong.valueOf(1), "appbuilder");
            Client m = TestDataFactory.createClient(M_CLIENT, "MMMM", "BUS", SecurityClientStatusCode.ACTIVE);
            when(appService.getAppByCode("appbuilder")).thenReturn(Mono.just(builder));
            when(clientService.getClientBy("MMMM")).thenReturn(Mono.just(m));
            Wallet w = new Wallet().setClientId(M_CLIENT).setAppId(BUILDER_APP_ID)
                    .setStatus(SecurityWalletStatus.SUSPENDED);
            w.setId(WALLET_ID);
            when(walletDAO.findByClientAndApp(M_CLIENT, BUILDER_APP_ID)).thenReturn(Mono.just(w));

            StepVerifier.create(service.isAiAllowed("appbuilder", "MMMM"))
                    .expectNext(false)
                    .verifyComplete();
        }

        @Test
        void aiAllowedTrueWhenNoWallet() {
            App builder = TestDataFactory.createOwnApp(BUILDER_APP_ID, ULong.valueOf(1), "appbuilder");
            Client m = TestDataFactory.createClient(M_CLIENT, "MMMM", "BUS", SecurityClientStatusCode.ACTIVE);
            when(appService.getAppByCode("appbuilder")).thenReturn(Mono.just(builder));
            when(clientService.getClientBy("MMMM")).thenReturn(Mono.just(m));
            when(walletDAO.findByClientAndApp(M_CLIENT, BUILDER_APP_ID)).thenReturn(Mono.empty());

            StepVerifier.create(service.isAiAllowed("appbuilder", "MMMM"))
                    .expectNext(true)
                    .verifyComplete();
        }
    }

    // ---------------------------------------------------------------------
    // Hosting gate
    // ---------------------------------------------------------------------

    @Nested
    class Hosting {

        @Test
        void systemClientAlwaysPassesThrough() {
            StepVerifier.create(service.resolveHosting("leadzump", "SYSTEM"))
                    .assertNext(d -> {
                        assertFalse(d.suspended());
                        assertEquals("leadzump", d.serveAppCode());
                        assertEquals("SYSTEM", d.serveClientCode());
                    })
                    .verifyComplete();
        }

        @Test
        void appWithSuspendedBuilderWalletServesSuspendTarget() {
            App app = TestDataFactory.createOwnApp(APP_ID, C_CLIENT, "leadzump"); // type APP
            App builder = TestDataFactory.createOwnApp(BUILDER_APP_ID, ULong.valueOf(1), "appbuilder");
            Client m = TestDataFactory.createClient(M_CLIENT, "MMMM", "BUS", SecurityClientStatusCode.ACTIVE);
            Wallet builderWallet = new Wallet().setClientId(M_CLIENT).setAppId(BUILDER_APP_ID)
                    .setStatus(SecurityWalletStatus.SUSPENDED);
            builderWallet.setId(WALLET_ID);
            AppBillingConfig cfg = new AppBillingConfig().setAppId(BUILDER_APP_ID).setClientId(C_CLIENT)
                    .setSuspendAppCode("suspended").setSuspendClientCode("SYSTEM");

            when(clientService.getClientBy("MMMM")).thenReturn(Mono.just(m));
            when(appService.getAppByCode("leadzump")).thenReturn(Mono.just(app));
            when(appService.getAppByCode("appbuilder")).thenReturn(Mono.just(builder));
            when(walletDAO.findByClientAndApp(M_CLIENT, BUILDER_APP_ID)).thenReturn(Mono.just(builderWallet));
            when(clientHierarchyService.getManagingClientIds(M_CLIENT)).thenReturn(Mono.just(List.of(C_CLIENT)));
            when(configService.readByAppAndClientId(BUILDER_APP_ID, C_CLIENT)).thenReturn(Mono.just(cfg));

            StepVerifier.create(service.resolveHosting("leadzump", "MMMM"))
                    .assertNext(d -> {
                        assertTrue(d.suspended());
                        assertEquals("suspended", d.serveAppCode());
                        assertEquals("SYSTEM", d.serveClientCode());
                    })
                    .verifyComplete();
        }

        @Test
        void appWithActiveBuilderWalletPassesThrough() {
            App app = TestDataFactory.createOwnApp(APP_ID, C_CLIENT, "leadzump");
            App builder = TestDataFactory.createOwnApp(BUILDER_APP_ID, ULong.valueOf(1), "appbuilder");
            Client m = TestDataFactory.createClient(M_CLIENT, "MMMM", "BUS", SecurityClientStatusCode.ACTIVE);
            Wallet builderWallet = new Wallet().setClientId(M_CLIENT).setAppId(BUILDER_APP_ID)
                    .setStatus(SecurityWalletStatus.ACTIVE);
            builderWallet.setId(WALLET_ID);

            when(clientService.getClientBy("MMMM")).thenReturn(Mono.just(m));
            when(appService.getAppByCode("leadzump")).thenReturn(Mono.just(app));
            when(appService.getAppByCode("appbuilder")).thenReturn(Mono.just(builder));
            when(walletDAO.findByClientAndApp(M_CLIENT, BUILDER_APP_ID)).thenReturn(Mono.just(builderWallet));

            StepVerifier.create(service.resolveHosting("leadzump", "MMMM"))
                    .assertNext(d -> {
                        assertFalse(d.suspended());
                        assertEquals("leadzump", d.serveAppCode());
                    })
                    .verifyComplete();
        }

        @Test
        void siteFallsBackToAppbuilderWalletWhenNoSitezumpWallet() {
            App site = TestDataFactory.createOwnApp(APP_ID, C_CLIENT, "mysite").setAppType(SecurityAppAppType.SITE);
            App sitezump = TestDataFactory.createOwnApp(SITEZUMP_APP_ID, ULong.valueOf(1), "sitezump");
            App builder = TestDataFactory.createOwnApp(BUILDER_APP_ID, ULong.valueOf(1), "appbuilder");
            Client m = TestDataFactory.createClient(M_CLIENT, "MMMM", "BUS", SecurityClientStatusCode.ACTIVE);
            Wallet builderWallet = new Wallet().setClientId(M_CLIENT).setAppId(BUILDER_APP_ID)
                    .setStatus(SecurityWalletStatus.ACTIVE);
            builderWallet.setId(WALLET_ID);

            when(clientService.getClientBy("MMMM")).thenReturn(Mono.just(m));
            when(appService.getAppByCode("mysite")).thenReturn(Mono.just(site));
            when(appService.getAppByCode("sitezump")).thenReturn(Mono.just(sitezump));
            when(appService.getAppByCode("appbuilder")).thenReturn(Mono.just(builder));
            // No sitezump wallet -> fall back to appbuilder wallet.
            when(walletDAO.findByClientAndApp(M_CLIENT, SITEZUMP_APP_ID)).thenReturn(Mono.empty());
            when(walletDAO.findByClientAndApp(M_CLIENT, BUILDER_APP_ID)).thenReturn(Mono.just(builderWallet));

            StepVerifier.create(service.resolveHosting("mysite", "MMMM"))
                    .assertNext(d -> assertFalse(d.suspended()))
                    .verifyComplete();

            verify(walletDAO).findByClientAndApp(M_CLIENT, SITEZUMP_APP_ID);
            verify(walletDAO).findByClientAndApp(M_CLIENT, BUILDER_APP_ID);
        }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private static EventQueObject argEvent(String name) {
        return org.mockito.ArgumentMatchers.argThat(
                e -> e != null && name.equals(((EventQueObject) e).getEventName()));
    }
}
