package com.fincity.security.integration.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fincity.security.dao.billing.AppBillingConfigDAO;
import com.fincity.security.integration.AbstractIntegrationTest;
import com.fincity.security.model.billing.BillingActionKeys;

import reactor.test.StepVerifier;

/**
 * Deep DB test for the metering instruction join: for a metered action, security
 * must stream exactly one (C, app, M) row per billed client M directly managed by
 * the config client C (client_hierarchy level0 = C), and only when the config is
 * ACTIVE and that action's rate is greater than zero.
 */
class AppBillingConfigDAOIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AppBillingConfigDAO configDAO;

    private ULong cClient; // configurator C
    private ULong mClient; // billed M, directly managed by C
    private ULong appId;

    @BeforeEach
    void setUp() {
        setupMockBeans();
        cClient = insertTestClient("CCFG", "Config Owner", "BUS").block();
        mClient = insertTestClient("MCFG", "Managed Client", "BUS").block();
        appId = insertTestApp(cClient, "cfgapp", "Config App").block();
        // M is directly managed by C.
        insertClientHierarchy(mClient, cClient, null, null, null).block();
        // config(C, app): a per-user rate set, app/site rent left at zero.
        insertConfig(cClient, appId, new BigDecimal("100"), "ACTIVE");
    }

    @AfterEach
    void tearDown() {
        databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
                .then(databaseClient.sql("DELETE FROM security_app_billing_config WHERE ID > 0").then())
                .then(databaseClient.sql("DELETE FROM security_client_hierarchy WHERE CLIENT_ID > 1").then())
                .then(databaseClient.sql("DELETE FROM security_app WHERE APP_CODE IN ('cfgapp')").then())
                .then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
                .then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
                .block();
    }

    private void insertConfig(ULong c, ULong app, java.math.BigDecimal userRate, String status) {
        databaseClient.sql(
                "INSERT INTO security_app_billing_config (CLIENT_ID, APP_ID, USER_TOKENS_PER_MONTH, STATUS) "
                        + "VALUES (:c, :app, :rate, :status)")
                .bind("c", c.longValue()).bind("app", app.longValue())
                .bind("rate", userRate).bind("status", status)
                .then().block();
    }

    @Test
    @DisplayName("a metered action with a rate streams (C, app, M) for the directly-managed client")
    void streamsInstructionForManagedClient() {
        StepVerifier.create(configDAO.chargeInstructions(BillingActionKeys.USER))
                .assertNext(instr -> {
                    assertEquals(cClient, instr.configClientId());
                    assertEquals("CCFG", instr.configClientCode());
                    assertEquals(appId, instr.appId());
                    assertEquals("cfgapp", instr.appCode());
                    assertEquals(mClient, instr.billedClientId());
                    assertEquals("MCFG", instr.billedClientCode());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("an action whose rate is zero on this config streams nothing")
    void zeroRateActionStreamsNothing() {
        // APP_RENT_PER_MONTH was left at its default of 0.
        StepVerifier.create(configDAO.chargeInstructions(BillingActionKeys.APP_RENT))
                .verifyComplete();
    }

    @Test
    @DisplayName("an INACTIVE config streams nothing")
    void inactiveConfigStreamsNothing() {
        databaseClient.sql("UPDATE security_app_billing_config SET STATUS = 'INACTIVE' WHERE CLIENT_ID = :c")
                .bind("c", cClient.longValue()).then().block();

        StepVerifier.create(configDAO.chargeInstructions(BillingActionKeys.USER))
                .verifyComplete();
    }

    @Test
    @DisplayName("an unknown action key streams nothing")
    void unknownActionStreamsNothing() {
        StepVerifier.create(configDAO.chargeInstructions("not.a.real.action"))
                .verifyComplete();
    }
}
