package com.fincity.security.integration.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fincity.security.dao.billing.WalletDAO;
import com.fincity.security.dao.billing.WalletTransactionDAO;
import com.fincity.security.dto.billing.WalletTransaction;
import com.fincity.security.integration.AbstractIntegrationTest;
import com.fincity.security.jooq.enums.SecurityWalletTransactionType;

import reactor.test.StepVerifier;

/**
 * Deep DB tests for the append-only ledger: the unique (wallet, idempotency_key)
 * is what makes a re-run of a 15-minute window (blue-green double-fire, retried
 * worker, reconcile) a true no-op. Also covers the reconcile window lookup.
 */
class WalletTransactionDAOIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WalletTransactionDAO txnDAO;
    @Autowired
    private WalletDAO walletDAO;

    private ULong walletId;
    private ULong appId;
    private static final String ACTION = "security.app.rent";
    private static final LocalDate DAY = LocalDate.of(2026, 6, 15);

    @BeforeEach
    void setUp() {
        setupMockBeans();
        ULong clientId = insertTestClient("LBUS", "Ledger Business", "BUS").block();
        appId = insertTestApp(clientId, "ledgerapp", "Ledger App").block();
        walletId = walletDAO.createSeeded(clientId, appId, BigDecimal.valueOf(100)).map(w -> w.getId()).block();
    }

    @AfterEach
    void tearDown() {
        databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
                .then(databaseClient.sql("DELETE FROM security_wallet_transaction WHERE ID > 0").then())
                .then(databaseClient.sql("DELETE FROM security_wallet WHERE ID > 0").then())
                .then(databaseClient.sql("DELETE FROM security_app WHERE ID > 0").then())
                .then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
                .then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
                .block();
    }

    private WalletTransaction debit(String idemKey, int window) {
        return new WalletTransaction()
                .setWalletId(walletId)
                .setType(SecurityWalletTransactionType.DEBIT)
                .setTokens(BigDecimal.ONE)
                .setBalanceAfter(BigDecimal.valueOf(99))
                .setActionKey(ACTION)
                .setAppId(appId)
                .setQuantity(BigDecimal.valueOf(5))
                .setChargeDate(DAY)
                .setWindowIndex((short) window)
                .setIdempotencyKey(idemKey)
                .setReason("metered " + ACTION + " window " + window);
    }

    private Long ledgerRowCount() {
        return databaseClient.sql("SELECT COUNT(*) c FROM security_wallet_transaction WHERE WALLET_ID = :w")
                .bind("w", walletId.longValue())
                .map(row -> row.get("c", Long.class))
                .one().block();
    }

    @Test
    @DisplayName("recordTxn inserts once; a duplicate idempotency key is ignored")
    void duplicateIdempotencyKeyIsIgnored() {
        String key = ACTION + ":" + DAY + ":7";

        StepVerifier.create(txnDAO.recordTxn(debit(key, 7))).expectNext(true).verifyComplete();
        StepVerifier.create(txnDAO.recordTxn(debit(key, 7))).expectNext(false).verifyComplete();

        assertEquals(1L, ledgerRowCount(), "the duplicate must not create a second ledger row");
    }

    @Test
    @DisplayName("different idempotency keys each insert")
    void differentKeysInsert() {
        StepVerifier.create(txnDAO.recordTxn(debit(ACTION + ":" + DAY + ":7", 7)))
                .expectNext(true).verifyComplete();
        StepVerifier.create(txnDAO.recordTxn(debit(ACTION + ":" + DAY + ":8", 8)))
                .expectNext(true).verifyComplete();

        assertEquals(2L, ledgerRowCount());
    }

    @Test
    @DisplayName("chargedWindows returns the set of windows already charged for (action, day)")
    void chargedWindowsReturnsRecordedWindows() {
        txnDAO.recordTxn(debit(ACTION + ":" + DAY + ":0", 0)).block();
        txnDAO.recordTxn(debit(ACTION + ":" + DAY + ":1", 1)).block();
        txnDAO.recordTxn(debit(ACTION + ":" + DAY + ":2", 2)).block();

        StepVerifier.create(txnDAO.chargedWindows(walletId, ACTION, DAY))
                .assertNext(windows -> {
                    List<Short> w = windows;
                    assertEquals(3, w.size());
                    assertTrue(w.containsAll(List.of((short) 0, (short) 1, (short) 2)));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("chargedWindows is empty for a day with no charges")
    void chargedWindowsEmptyForUnchargedDay() {
        StepVerifier.create(txnDAO.chargedWindows(walletId, ACTION, DAY))
                .assertNext(w -> assertTrue(w.isEmpty()))
                .verifyComplete();
    }
}
