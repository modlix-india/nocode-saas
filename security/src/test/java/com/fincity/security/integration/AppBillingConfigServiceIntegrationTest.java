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
import com.fincity.security.dto.billing.AppBillingConfig;
import com.fincity.security.service.billing.AppBillingConfigService;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * End-to-end (real MySQL + method security) test of the billing-config service:
 * create requires app write access + management of the config client, reads are
 * restricted to the caller's own + managed clients, and the generic list is
 * always forbidden.
 */
class AppBillingConfigServiceIntegrationTest extends AbstractIntegrationTest {

    private static final String APP_CREATE = "Authorities.Application_CREATE";

    @Autowired
    private AppBillingConfigService configService;

    private ULong cClient;  // configurator C, owns the app
    private ULong parent;   // manages C
    private ULong stranger; // unrelated client
    private ULong appId;

    @BeforeEach
    void setUp() {
        setupMockBeans();
        cClient = insertTestClient("CCFG", "Config Owner", "BUS").block();
        parent = insertTestClient("PCFG", "Parent Client", "BUS").block();
        stranger = insertTestClient("XCFG", "Stranger", "BUS").block();
        appId = insertTestApp(cClient, "cfgsvc", "Config Svc App").block();
        // parent manages C (hierarchy level0 = parent).
        insertClientHierarchy(cClient, parent, null, null, null).block();
        insertClientHierarchy(parent, null, null, null, null).block();
        insertClientHierarchy(stranger, null, null, null, null).block();
    }

    @AfterEach
    void tearDown() {
        databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
                .then(databaseClient.sql("DELETE FROM security_app_billing_config WHERE ID > 0").then())
                .then(databaseClient.sql("DELETE FROM security_client_hierarchy WHERE CLIENT_ID > 1").then())
                .then(databaseClient.sql("DELETE FROM security_app WHERE APP_CODE IN ('cfgsvc')").then())
                .then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
                .then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
                .block();
    }

    private ContextAuthentication auth(ULong clientId, String code) {
        return TestDataFactory.createBusinessAuth(clientId, code,
                List.of(APP_CREATE, "Authorities.ROLE_Owner", "Authorities.Logged_IN"));
    }

    private <T> Mono<T> as(Mono<T> mono, ContextAuthentication ca) {
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca));
    }

    private AppBillingConfig newConfig(ULong client) {
        return new AppBillingConfig().setAppId(appId).setClientId(client)
                .setUserTokensPerMonth(BigDecimal.valueOf(2880)).setStatus(
                        com.fincity.security.jooq.enums.SecurityAppBillingConfigStatus.ACTIVE);
    }

    private ULong seedConfig(ULong client) {
        return databaseClient.sql(
                "INSERT INTO security_app_billing_config (CLIENT_ID, APP_ID, STATUS) VALUES (:c, :app, 'ACTIVE')")
                .bind("c", client.longValue()).bind("app", appId.longValue())
                .filter(s -> s.returnGeneratedValues("ID"))
                .map(row -> ULong.valueOf(row.get("ID", Long.class)))
                .one().block();
    }

    @Test
    @DisplayName("create succeeds when the caller owns the app and configures its own client")
    void createForOwnAppSucceeds() {
        StepVerifier.create(as(configService.create(newConfig(cClient)), auth(cClient, "CCFG")))
                .assertNext(saved -> {
                    assertEquals(cClient, saved.getClientId());
                    assertEquals(appId, saved.getAppId());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("create is forbidden when the caller has no write access to the app")
    void createForbiddenWithoutAppAccess() {
        // stranger has Application_CREATE but neither owns the app nor manages cClient.
        StepVerifier.create(as(configService.create(newConfig(cClient)), auth(stranger, "XCFG")))
                .verifyError(GenericException.class);
    }

    @Test
    @DisplayName("read returns the config to its own client and to a managing client, but not to a stranger")
    void readRestrictedToOwnAndManaged() {
        ULong id = seedConfig(cClient);

        StepVerifier.create(as(configService.read(id), auth(cClient, "CCFG")))
                .assertNext(c -> assertEquals(id, c.getId()))
                .verifyComplete();

        StepVerifier.create(as(configService.read(id), auth(parent, "PCFG")))
                .assertNext(c -> assertEquals(id, c.getId()))
                .verifyComplete();

        StepVerifier.create(as(configService.read(id), auth(stranger, "XCFG")))
                .verifyComplete(); // filtered out -> empty
    }

    @Test
    @DisplayName("findByApp returns only the configs the caller can see")
    void findByAppFiltersToVisible() {
        seedConfig(cClient);
        seedConfig(stranger); // a config on the same app owned by an unrelated client

        StepVerifier.create(as(configService.findByApp(appId), auth(cClient, "CCFG")))
                .assertNext(list -> {
                    assertEquals(1, list.size(), "only the caller's own config is visible");
                    assertEquals(cClient, list.get(0).getClientId());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("readByAppAndClient resolves a config by app + client codes")
    void readByAppAndClientResolvesByCodes() {
        seedConfig(cClient);
        StepVerifier.create(configService.readByAppAndClient("cfgsvc", "CCFG"))
                .assertNext(c -> assertEquals(cClient, c.getClientId()))
                .verifyComplete();
    }

    @Test
    @DisplayName("the generic list (readPageFilter / readAllFilter) is always forbidden")
    void genericListForbidden() {
        StepVerifier.create(configService.readPageFilter(
                org.springframework.data.domain.PageRequest.of(0, 10), null))
                .verifyError(GenericException.class);
        StepVerifier.create(configService.readAllFilter(null))
                .verifyError(GenericException.class);
    }
}
