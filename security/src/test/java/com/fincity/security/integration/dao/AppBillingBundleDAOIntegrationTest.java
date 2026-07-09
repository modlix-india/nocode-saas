package com.fincity.security.integration.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fincity.security.dao.billing.AppBillingBundleDAO;
import com.fincity.security.integration.AbstractIntegrationTest;

import reactor.test.StepVerifier;

/**
 * Deep DB test for bundle lookup: findByConfigId returns the config's non-DELETED
 * bundles ordered by DISPLAY_ORDER, and nothing for a config with no bundles.
 */
class AppBillingBundleDAOIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AppBillingBundleDAO bundleDAO;

    private ULong cClient;
    private ULong appId;
    private ULong configId;
    private ULong emptyConfigId;

    @BeforeEach
    void setUp() {
        setupMockBeans();
        cClient = insertTestClient("CBDL", "Bundle Config Owner", "BUS").block();
        appId = insertTestApp(cClient, "bdlapp", "Bundle App").block();
        configId = insertConfig(cClient, appId);
        emptyConfigId = insertConfig(cClient, insertTestApp(cClient, "bdlapp2", "Bundle App 2").block());
    }

    @AfterEach
    void tearDown() {
        databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
                .then(databaseClient.sql("DELETE FROM security_app_billing_bundle WHERE ID > 0").then())
                .then(databaseClient.sql("DELETE FROM security_app_billing_config WHERE ID > 0").then())
                .then(databaseClient.sql("DELETE FROM security_app WHERE ID > 0").then())
                .then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
                .then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
                .block();
    }

    @Test
    @DisplayName("findByConfigId returns non-DELETED bundles ordered by display order")
    void returnsActiveBundlesInDisplayOrder() {
        insertBundle(configId, "Second", 2, "ACTIVE");
        insertBundle(configId, "First", 1, "ACTIVE");
        insertBundle(configId, "Gone", 0, "DELETED");

        StepVerifier.create(bundleDAO.findByConfigId(configId))
                .assertNext(list -> {
                    assertEquals(2, list.size(), "DELETED excluded");
                    assertEquals("First", list.get(0).getLabel(), "ordered by display order asc");
                    assertEquals("Second", list.get(1).getLabel());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("findByConfigId returns an empty list for a config with no bundles")
    void returnsEmptyForConfigWithoutBundles() {
        StepVerifier.create(bundleDAO.findByConfigId(emptyConfigId))
                .assertNext(list -> assertEquals(0, list.size()))
                .verifyComplete();
    }

    private ULong insertConfig(ULong c, ULong app) {
        return databaseClient.sql(
                "INSERT INTO security_app_billing_config (CLIENT_ID, APP_ID, STATUS) VALUES (:c, :app, 'ACTIVE')")
                .bind("c", c.longValue()).bind("app", app.longValue())
                .filter(s -> s.returnGeneratedValues("ID"))
                .map(row -> ULong.valueOf(row.get("ID", Long.class)))
                .one().block();
    }

    private void insertBundle(ULong config, String label, int order, String status) {
        databaseClient.sql(
                "INSERT INTO security_app_billing_bundle (BILLING_CONFIG_ID, LABEL, BUNDLE_TYPE, TOKENS, PRICE, DISPLAY_ORDER, STATUS) "
                        + "VALUES (:cfg, :label, 'FIXED', 1000, 100, :ord, :status)")
                .bind("cfg", config.longValue()).bind("label", label).bind("ord", order).bind("status", status)
                .then().block();
    }
}
