package com.fincity.security.integration.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fincity.security.dao.billing.WalletDAO;
import com.fincity.security.integration.AbstractIntegrationTest;
import com.fincity.security.jooq.enums.SecurityWalletStatus;

import reactor.test.StepVerifier;

/**
 * Deep DB tests for the wallet primitives against a real MySQL (testcontainer):
 * the atomic ACTIVE-gated debit and the idempotent lazy seed are the two pieces
 * the whole billing engine relies on for exactly-once charging.
 */
class WalletDAOIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WalletDAO walletDAO;

    private ULong clientId;
    private ULong appId;

    @BeforeEach
    void setUp() {
        setupMockBeans();
        clientId = insertTestClient("WBUS", "Wallet Business", "BUS").block();
        appId = insertTestApp(clientId, "walletapp", "Wallet App").block();
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

    private ULong seed(BigDecimal initial) {
        return walletDAO.createSeeded(clientId, appId, initial).map(w -> w.getId()).block();
    }

    @Test
    @DisplayName("createSeeded is idempotent: a second seed does not stack balance")
    void seedIsIdempotent() {
        ULong first = seed(BigDecimal.ONE);
        ULong second = seed(BigDecimal.ONE);

        assertEquals(first, second, "same (client, app) must resolve to the same wallet");
        StepVerifier.create(walletDAO.findByClientAndApp(clientId, appId))
                .assertNext(w -> assertEquals(0, w.getBalance().compareTo(BigDecimal.ONE),
                        "balance must stay 1, not double"))
                .verifyComplete();
    }

    @Test
    @DisplayName("debitActive reduces balance and reports one row when ACTIVE")
    void debitActiveWhenActive() {
        ULong id = seed(BigDecimal.valueOf(100));

        StepVerifier.create(walletDAO.debitActive(id, BigDecimal.valueOf(30)))
                .expectNext(1)
                .verifyComplete();

        StepVerifier.create(walletDAO.findByClientAndApp(clientId, appId))
                .assertNext(w -> assertEquals(0, w.getBalance().compareTo(BigDecimal.valueOf(70))))
                .verifyComplete();
    }

    @Test
    @DisplayName("debitActive is a no-op (0 rows, balance unchanged) when SUSPENDED")
    void debitActiveWhenSuspended() {
        ULong id = seed(BigDecimal.valueOf(100));
        walletDAO.setStatus(id, SecurityWalletStatus.SUSPENDED).block();

        StepVerifier.create(walletDAO.debitActive(id, BigDecimal.valueOf(30)))
                .expectNext(0)
                .verifyComplete();

        StepVerifier.create(walletDAO.findByClientAndApp(clientId, appId))
                .assertNext(w -> {
                    assertEquals(SecurityWalletStatus.SUSPENDED, w.getStatus());
                    assertEquals(0, w.getBalance().compareTo(BigDecimal.valueOf(100)), "balance must be unchanged");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("a debit may drive balance negative (the one overshoot), then SUSPENDED debits stop")
    void debitMayCrossZeroOnceThenStops() {
        ULong id = seed(BigDecimal.valueOf(10));

        // Crosses zero: still ACTIVE at debit time, so it applies.
        StepVerifier.create(walletDAO.debitActive(id, BigDecimal.valueOf(30))).expectNext(1).verifyComplete();
        // Caller suspends after seeing negative balance.
        walletDAO.setStatus(id, SecurityWalletStatus.SUSPENDED).block();
        // Next debit is blocked.
        StepVerifier.create(walletDAO.debitActive(id, BigDecimal.valueOf(5))).expectNext(0).verifyComplete();

        StepVerifier.create(walletDAO.findByClientAndApp(clientId, appId))
                .assertNext(w -> assertEquals(0, w.getBalance().compareTo(BigDecimal.valueOf(-20))))
                .verifyComplete();
    }

    @Test
    @DisplayName("creditBalance increases the balance")
    void creditBalanceAddsTokens() {
        ULong id = seed(BigDecimal.valueOf(10));

        StepVerifier.create(walletDAO.creditBalance(id, BigDecimal.valueOf(90))).expectNext(1).verifyComplete();

        StepVerifier.create(walletDAO.findByClientAndApp(clientId, appId))
                .assertNext(w -> assertEquals(0, w.getBalance().compareTo(BigDecimal.valueOf(100))))
                .verifyComplete();
    }

    @Test
    @DisplayName("findByClientAndApp returns empty for an unknown (client, app)")
    void findReturnsEmptyWhenAbsent() {
        assertNotNull(appId);
        StepVerifier.create(walletDAO.findByClientAndApp(clientId, appId)).verifyComplete();
    }
}
