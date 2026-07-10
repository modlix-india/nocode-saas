package com.fincity.security.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fincity.security.dao.billing.WalletDAO;
import com.fincity.security.service.billing.SecurityMeteringService;

/**
 * End-to-end security metering against real MySQL: runWindow resolves the metering
 * instructions, counts the real users-with-profile-in-app, prices via config and
 * debits the billed client's wallet with an idempotent per-window ledger; the
 * nightly reconcile back-fills every window missed and is safe to re-run.
 */
class SecurityMeteringServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SecurityMeteringService meteringService;
    @Autowired
    private WalletDAO walletDAO;

    // June 2026: 30 days -> windowsInMonth = 2880, so rate 2880 = 1 token/user/window.
    private static final LocalDate DAY = LocalDate.of(2026, 6, 15);
    private static final int USERS = 3;

    private ULong cClient; // configurator C, owns the app
    private ULong mClient; // billed client M, managed by C
    private ULong appId;

    @BeforeEach
    void setUp() {
        setupMockBeans();
        cClient = insertTestClient("CMET", "Metering Config", "BUS").block();
        mClient = insertTestClient("MMET", "Metered Client", "BUS").block();
        appId = insertTestApp(cClient, "metapp", "Metering App").block();
        insertClientHierarchy(mClient, cClient, null, null, null).block(); // M directly managed by C
        insertClientHierarchy(cClient, null, null, null, null).block();
        seedUserConfig(cClient, appId, new BigDecimal("2880"));

        ULong profile = insertProfile(mClient, appId);
        for (int i = 0; i < USERS; i++) {
            ULong u = insertTestUser(mClient, "metu" + i, "metu" + i + "@test.com", "x").block();
            assignProfile(profile, u);
        }
        walletDAO.createSeeded(mClient, appId, BigDecimal.valueOf(10000)).block();
    }

    @AfterEach
    void tearDown() {
        databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
                .then(databaseClient.sql("DELETE FROM security_wallet_transaction WHERE ID > 0").then())
                .then(databaseClient.sql("DELETE FROM security_wallet WHERE ID > 0").then())
                .then(databaseClient.sql("DELETE FROM security_app_billing_config WHERE ID > 0").then())
                .then(databaseClient.sql("DELETE FROM security_profile_user WHERE USER_ID > 1").then())
                .then(databaseClient.sql("DELETE FROM security_profile WHERE CLIENT_ID > 1").then())
                .then(databaseClient.sql("DELETE FROM security_user WHERE ID > 1").then())
                .then(databaseClient.sql("DELETE FROM security_client_hierarchy WHERE CLIENT_ID > 1").then())
                .then(databaseClient.sql("DELETE FROM security_app WHERE APP_CODE IN ('metapp')").then())
                .then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
                .then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
                .block();
    }

    @Test
    @DisplayName("runWindow counts the real users, prices via config, and debits the wallet once for the window")
    void runWindowChargesFromRealCount() {
        meteringService.runWindow(DAY, 5).block();

        assertEquals(0, balance().compareTo(BigDecimal.valueOf(10000 - USERS)), "3 users x 1 token/window");
        assertEquals(1L, debitCount(), "exactly one debit for the window");

        // Re-running the same window is a no-op (idempotent per {action}:{date}:{window}).
        meteringService.runWindow(DAY, 5).block();
        assertEquals(0, balance().compareTo(BigDecimal.valueOf(10000 - USERS)));
        assertEquals(1L, debitCount());
    }

    @Test
    @DisplayName("reconcileDay back-fills every window missed and is idempotent")
    void reconcileFillsMissingWindowsIdempotently() {
        meteringService.runWindow(DAY, 5).block(); // one window charged live
        meteringService.reconcileDay(DAY).block();  // fill the other 95

        long fullDay = 96L * USERS;
        assertEquals(96L, debitCount(), "every window of the day is charged exactly once");
        assertEquals(0, balance().compareTo(BigDecimal.valueOf(10000 - fullDay)));

        meteringService.reconcileDay(DAY).block(); // re-run
        assertEquals(96L, debitCount(), "no double-charge on a second reconcile");
        assertEquals(0, balance().compareTo(BigDecimal.valueOf(10000 - fullDay)));
    }

    // --- helpers ---

    private BigDecimal balance() {
        return walletDAO.findByClientAndApp(mClient, appId).map(w -> w.getBalance()).block();
    }

    private Long debitCount() {
        return databaseClient.sql("SELECT COUNT(*) c FROM security_wallet_transaction t "
                + "JOIN security_wallet w ON w.ID = t.WALLET_ID "
                + "WHERE w.CLIENT_ID = :m AND w.APP_ID = :app AND t.TYPE = 'DEBIT'")
                .bind("m", mClient.longValue()).bind("app", appId.longValue())
                .map(row -> row.get("c", Long.class)).one().block();
    }

    private void seedUserConfig(ULong c, ULong app, BigDecimal userRate) {
        databaseClient.sql(
                "INSERT INTO security_app_billing_config (CLIENT_ID, APP_ID, USER_TOKENS_PER_MONTH, STATUS) "
                        + "VALUES (:c, :app, :rate, 'ACTIVE')")
                .bind("c", c.longValue()).bind("app", app.longValue()).bind("rate", userRate)
                .then().block();
    }

    private ULong insertProfile(ULong clientId, ULong app) {
        return databaseClient.sql(
                "INSERT INTO security_profile (CLIENT_ID, APP_ID, NAME, DESCRIPTION) VALUES (:c, :app, 'MP', 'MP')")
                .bind("c", clientId.longValue()).bind("app", app.longValue())
                .filter(s -> s.returnGeneratedValues("ID"))
                .map(row -> ULong.valueOf(row.get("ID", Long.class)))
                .one().block();
    }

    private void assignProfile(ULong profileId, ULong userId) {
        databaseClient.sql("INSERT INTO security_profile_user (PROFILE_ID, USER_ID) VALUES (:p, :u)")
                .bind("p", profileId.longValue()).bind("u", userId.longValue())
                .then().block();
    }
}
