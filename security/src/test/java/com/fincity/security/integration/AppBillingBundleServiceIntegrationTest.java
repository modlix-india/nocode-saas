package com.fincity.security.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.security.dto.billing.AppBillingBundle;
import com.fincity.security.jooq.enums.SecurityAppBillingBundleBundleType;
import com.fincity.security.jooq.enums.SecurityAppBillingBundleStatus;
import com.fincity.security.service.billing.AppBillingBundleService;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * End-to-end (real MySQL + method security) test of the bundle service: mutations
 * are gated by the caller's ability to manage the owning config; the bundle list
 * for a config is ungated (buyers pick from it); the generic list is forbidden.
 */
class AppBillingBundleServiceIntegrationTest extends AbstractIntegrationTest {

    private static final String APP_CREATE = "Authorities.Application_CREATE";

    @Autowired
    private AppBillingBundleService bundleService;

    private ULong cClient;
    private ULong stranger;
    private ULong appId;
    private ULong configId;

    @BeforeEach
    void setUp() {
        setupMockBeans();
        cClient = insertTestClient("CBND", "Bundle Owner", "BUS").block();
        stranger = insertTestClient("XBND", "Stranger", "BUS").block();
        appId = insertTestApp(cClient, "bndsvc", "Bundle Svc App").block();
        insertClientHierarchy(cClient, null, null, null, null).block();
        insertClientHierarchy(stranger, null, null, null, null).block();
        configId = seedConfig(cClient);
    }

    @AfterEach
    void tearDown() {
        databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
                .then(databaseClient.sql("DELETE FROM security_app_billing_bundle WHERE ID > 0").then())
                .then(databaseClient.sql("DELETE FROM security_app_billing_config WHERE ID > 0").then())
                .then(databaseClient.sql("DELETE FROM security_client_hierarchy WHERE CLIENT_ID > 1").then())
                .then(databaseClient.sql("DELETE FROM security_app WHERE APP_CODE IN ('bndsvc')").then())
                .then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
                .then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
                .block();
    }

    private ContextAuthentication auth(ULong clientId, String code) {
        return TestDataFactory.createBusinessAuth(clientId, code, List.of(APP_CREATE, "Authorities.Logged_IN"));
    }

    private <T> Mono<T> as(Mono<T> mono, ContextAuthentication ca) {
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca));
    }

    private ULong seedConfig(ULong client) {
        return databaseClient.sql(
                "INSERT INTO security_app_billing_config (CLIENT_ID, APP_ID, STATUS) VALUES (:c, :app, 'ACTIVE')")
                .bind("c", client.longValue()).bind("app", appId.longValue())
                .filter(s -> s.returnGeneratedValues("ID"))
                .map(row -> ULong.valueOf(row.get("ID", Long.class)))
                .one().block();
    }

    private AppBillingBundle fixedBundle() {
        return new AppBillingBundle().setBillingConfigId(configId).setLabel("Starter")
                .setBundleType(SecurityAppBillingBundleBundleType.FIXED)
                .setTokens(BigDecimal.valueOf(1000)).setPrice(BigDecimal.valueOf(100))
                .setCurrency("INR").setStatus(SecurityAppBillingBundleStatus.ACTIVE).setDisplayOrder(1);
    }

    @Test
    @DisplayName("create succeeds for a caller that manages the owning config")
    void createSucceedsForConfigManager() {
        StepVerifier.create(as(bundleService.create(fixedBundle()), auth(cClient, "CBND")))
                .assertNext(b -> {
                    assertEquals("Starter", b.getLabel());
                    assertEquals(configId, b.getBillingConfigId());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("create is forbidden for a caller that cannot manage the owning config")
    void createForbiddenForStranger() {
        StepVerifier.create(as(bundleService.create(fixedBundle()), auth(stranger, "XBND")))
                .verifyError(GenericException.class);
    }

    @Test
    @DisplayName("findByConfigId (ungated) returns the config's bundles")
    void findByConfigIdReturnsBundles() {
        bundleService.create(fixedBundle())
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth(cClient, "CBND")))
                .block();

        StepVerifier.create(bundleService.findByConfigId(configId))
                .assertNext(list -> assertEquals(1, list.size()))
                .verifyComplete();
    }

    @Test
    @DisplayName("readForPurchase returns the bundle ungated")
    void readForPurchaseReturnsBundle() {
        AppBillingBundle created = bundleService.create(fixedBundle())
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth(cClient, "CBND")))
                .block();

        StepVerifier.create(bundleService.readForPurchase(created.getId()))
                .assertNext(b -> assertEquals(created.getId(), b.getId()))
                .verifyComplete();
    }

    @Test
    @DisplayName("the generic list (readPageFilter / readAllFilter) is always forbidden")
    void genericListForbidden() {
        StepVerifier.create(bundleService.readPageFilter(
                org.springframework.data.domain.PageRequest.of(0, 10), null))
                .verifyError(GenericException.class);
        StepVerifier.create(bundleService.readAllFilter(null))
                .verifyError(GenericException.class);
    }
}
