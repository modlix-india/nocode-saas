package com.fincity.security.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;

import com.fincity.saas.commons.mq.events.EventNames;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.security.dao.billing.WalletDAO;
import com.fincity.security.dto.billing.Invoice;
import com.fincity.security.jooq.enums.SecurityWalletStatus;
import com.fincity.security.model.billing.BillingActionKeys;
import com.fincity.security.model.billing.ChargeRequest;
import com.fincity.security.service.billing.WalletService;

import reactor.test.StepVerifier;

/**
 * Deep end-to-end test of the wallet engine wired by Spring against a real MySQL:
 * real config resolution + pricing, the ledger-first idempotent debit, edge-
 * triggered alert latch, zero-crossing suspension and credit reactivation, with
 * balance always equal to the sum of the ledger.
 */
class WalletServiceBillingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WalletService walletService;
    @Autowired
    private WalletDAO walletDAO;

    private ULong cClient;
    private ULong mClient;
    private ULong appId;
    // June 2026: 30 days -> windowsInMonth = 2880.
    private static final LocalDate DAY = LocalDate.of(2026, 6, 15);

    @BeforeEach
    void setUp() {
        setupMockBeans();
        cClient = insertTestClient("CWS", "Config Owner", "BUS").block();
        mClient = insertTestClient("MWS", "Billed Client", "BUS").block();
        appId = insertTestApp(cClient, "wsapp", "WS App").block();
        insertClientHierarchy(mClient, cClient, null, null, null).block();
    }

    @AfterEach
    void tearDown() {
        databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
                .then(databaseClient.sql("DELETE FROM security_wallet_transaction WHERE ID > 0").then())
                .then(databaseClient.sql("DELETE FROM security_wallet WHERE ID > 0").then())
                .then(databaseClient.sql("DELETE FROM security_app_billing_config WHERE ID > 0").then())
                .then(databaseClient.sql("DELETE FROM security_client_hierarchy WHERE CLIENT_ID > 1").then())
                .then(databaseClient.sql("DELETE FROM security_app WHERE APP_CODE IN ('wsapp')").then())
                .then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
                .then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
                .block();
    }

    /** rate tokens/month for security.user, optional low-balance threshold. */
    private void insertConfig(BigDecimal userRate, BigDecimal threshold) {
        var spec = databaseClient.sql(
                "INSERT INTO security_app_billing_config (CLIENT_ID, APP_ID, USER_TOKENS_PER_MONTH, LOW_BALANCE_THRESHOLD, STATUS) "
                        + "VALUES (:c, :app, :rate, :thr, 'ACTIVE')")
                .bind("c", cClient.longValue()).bind("app", appId.longValue()).bind("rate", userRate);
        spec = threshold == null ? spec.bindNull("thr", BigDecimal.class) : spec.bind("thr", threshold);
        spec.then().block();
    }

    private void seedWallet(BigDecimal balance) {
        walletDAO.createSeeded(mClient, appId, balance).block();
    }

    private BigDecimal balance() {
        return walletDAO.findByClientAndApp(mClient, appId).map(w -> w.getBalance()).block();
    }

    private SecurityWalletStatus status() {
        return walletDAO.findByClientAndApp(mClient, appId).map(w -> w.getStatus()).block();
    }

    private BigDecimal ledgerDebitSum() {
        return databaseClient.sql(
                "SELECT COALESCE(SUM(TOKENS),0) s FROM security_wallet_transaction t "
                        + "JOIN security_wallet w ON w.ID = t.WALLET_ID "
                        + "WHERE w.CLIENT_ID = :c AND w.APP_ID = :app AND t.TYPE = 'DEBIT'")
                .bind("c", mClient.longValue()).bind("app", appId.longValue())
                .map(row -> row.get("s", BigDecimal.class)).one().block();
    }

    private ChargeRequest userCharge(int qty, int window) {
        return new ChargeRequest(cClient, mClient, appId, BillingActionKeys.USER,
                BigDecimal.valueOf(qty), DAY, window);
    }

    @Test
    @DisplayName("a charge prices via config, writes the ledger, and balance == initial - sum(debits)")
    void chargeDebitsAndLedgers() {
        insertConfig(BigDecimal.valueOf(2880), null); // 2880/mo over 2880 windows = 1 token/user/window
        seedWallet(BigDecimal.valueOf(100));

        // qty 30 -> floor(30 * 2880 / 2880) = 30 tokens.
        StepVerifier.create(walletService.charge(userCharge(30, 7)))
                .assertNext(r -> assertEquals(true, r.charged()))
                .verifyComplete();

        assertEquals(0, balance().compareTo(BigDecimal.valueOf(70)));
        assertEquals(0, ledgerDebitSum().compareTo(BigDecimal.valueOf(30)));
        assertEquals(0, balance().compareTo(BigDecimal.valueOf(100).subtract(ledgerDebitSum())));
    }

    @Test
    @DisplayName("re-charging the same window is a no-op (exactly-once) — the reconcile guarantee")
    void rechargeSameWindowIsNoOp() {
        insertConfig(BigDecimal.valueOf(2880), null);
        seedWallet(BigDecimal.valueOf(100));

        walletService.charge(userCharge(30, 7)).block();
        walletService.charge(userCharge(30, 7)).block(); // same idempotency key

        assertEquals(0, balance().compareTo(BigDecimal.valueOf(70)), "balance must not double-debit");
        assertEquals(0, ledgerDebitSum().compareTo(BigDecimal.valueOf(30)), "only one ledger debit");
    }

    @Test
    @DisplayName("a downward crossing of the threshold latches and raises WALLET_LOW_BALANCE")
    void lowBalanceCrossingRaisesEventAndLatches() {
        insertConfig(BigDecimal.valueOf(2880), BigDecimal.valueOf(400));
        seedWallet(BigDecimal.valueOf(420));

        // 420 - 30 -> 390 crosses 400 downward.
        StepVerifier.create(walletService.charge(userCharge(30, 7)))
                .assertNext(r -> assertEquals(true, r.lowBalanceCrossed()))
                .verifyComplete();

        Byte notified = walletDAO.findByClientAndApp(mClient, appId).map(w -> w.getLowBalanceNotified()).block();
        assertEquals((byte) 1, notified.byteValue(), "latch must be set");

        ArgumentCaptor<EventQueObject> cap = ArgumentCaptor.forClass(EventQueObject.class);
        verify(eventCreationService, atLeastOnce()).createEvent(cap.capture());
        boolean lowRaised = cap.getAllValues().stream()
                .anyMatch(e -> EventNames.WALLET_LOW_BALANCE.equals(e.getEventName()));
        org.junit.jupiter.api.Assertions.assertTrue(lowRaised, "WALLET_LOW_BALANCE must be raised");
    }

    @Test
    @DisplayName("crossing zero suspends the wallet; further charges are blocked")
    void zeroCrossingSuspendsAndBlocks() {
        insertConfig(BigDecimal.valueOf(2880), null);
        seedWallet(BigDecimal.valueOf(10));

        // 10 - 30 -> -20 : applies (was ACTIVE), then suspends.
        StepVerifier.create(walletService.charge(userCharge(30, 7)))
                .assertNext(r -> {
                    assertEquals(true, r.charged());
                    assertEquals(true, r.suspended());
                })
                .verifyComplete();
        assertEquals(SecurityWalletStatus.SUSPENDED, status());
        assertEquals(0, balance().compareTo(BigDecimal.valueOf(-20)));

        // A later window is blocked (SUSPENDED), balance unchanged.
        walletService.charge(userCharge(30, 8)).block();
        assertEquals(0, balance().compareTo(BigDecimal.valueOf(-20)));
    }

    @Test
    @DisplayName("credit from a paid invoice reactivates a suspended wallet and clears the latch")
    void creditReactivates() {
        insertConfig(BigDecimal.valueOf(2880), BigDecimal.valueOf(400));
        seedWallet(BigDecimal.valueOf(10));
        walletService.charge(userCharge(30, 7)).block(); // -> -20, SUSPENDED
        assertEquals(SecurityWalletStatus.SUSPENDED, status());

        Invoice invoice = new Invoice().setClientId(mClient).setAppId(appId)
                .setTokensPurchased(BigDecimal.valueOf(100)).setPaymentReference("pay_int_1")
                .setInvoiceNumber("INV/int/1");

        StepVerifier.create(walletService.creditFromPayment(invoice))
                .assertNext(r -> assertEquals(true, r.charged()))
                .verifyComplete();

        assertEquals(SecurityWalletStatus.ACTIVE, status());
        assertEquals(0, balance().compareTo(BigDecimal.valueOf(80))); // -20 + 100
        Byte notified = walletDAO.findByClientAndApp(mClient, appId).map(w -> w.getLowBalanceNotified()).block();
        assertEquals((byte) 0, notified.byteValue(), "latch cleared on credit");
    }
}
