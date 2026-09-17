package com.fincity.security.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fincity.saas.commons.service.CacheService;
import com.fincity.security.dao.billing.WalletDAO;
import com.fincity.security.model.billing.AiChargeRequest;
import com.fincity.security.model.billing.BillingActionKeys;
import com.fincity.security.model.billing.ChargeRequest;

/**
 * HTTP-level test of the cluster-only internal billing endpoints (permitAll, no
 * token): a bulk charge debits the wallet, instructions stream the managed client,
 * and the AI gate / serving status resolve by code. Exercises controller wiring +
 * JSON (de)serialization end to end against the running server + real MySQL.
 */
class InternalBillingControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "/api/security/internal/billing";

    @LocalServerPort
    private int port;
    @Autowired
    private WalletDAO walletDAO;
    @Autowired
    private CacheService cacheService;
    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private WebTestClient client;

    private ULong cClient;
    private ULong mClient;
    private ULong appId;

    @BeforeEach
    void setUp() {
        setupMockBeans();
        cacheService.evictAll("byAppCode").block();
        cacheService.evictAll("clientCodeId").block();
        cacheService.evictAll("appBillingConfigByCodes").block();

        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30)).build();

        cClient = insertTestClient("CINT", "Internal Config", "BUS").block();
        mClient = insertTestClient("MINT", "Internal Managed", "BUS").block();
        appId = insertTestApp(cClient, "intapp", "Internal App").block();
        insertClientHierarchy(mClient, cClient, null, null, null).block();
        insertClientHierarchy(cClient, null, null, null, null).block();
        seedUserConfig(cClient, appId, new BigDecimal("2880"));
        walletDAO.createSeeded(mClient, appId, BigDecimal.valueOf(1000)).block();
    }

    @AfterEach
    void tearDown() {
        databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
                .then(databaseClient.sql("DELETE FROM security_wallet_transaction WHERE ID > 0").then())
                .then(databaseClient.sql("DELETE FROM security_wallet WHERE ID > 0").then())
                .then(databaseClient.sql("DELETE FROM security_app_billing_config WHERE ID > 0").then())
                .then(databaseClient.sql("DELETE FROM security_client_hierarchy WHERE CLIENT_ID > 1").then())
                .then(databaseClient.sql("DELETE FROM security_app WHERE APP_CODE IN ('intapp')").then())
                .then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
                .then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
                .block();
    }

    @Test
    @DisplayName("POST /charge prices the bulk request from config and debits the wallet")
    void bulkChargeDebitsWallet() throws Exception {
        // Serialize with the app's ObjectMapper so the body matches the exact wire
        // format the feign callers (core/entity-processor) produce for ChargeRequest.
        ChargeRequest req = new ChargeRequest(cClient, mClient, appId, BillingActionKeys.USER,
                BigDecimal.valueOf(3), LocalDate.of(2026, 6, 15), 5);
        String body = objectMapper.writeValueAsString(List.of(req));

        client.post().uri(BASE + "/charge")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Boolean.class).isEqualTo(Boolean.TRUE);

        // 3 users-worth quantity, rate 2880 over June's 2880 windows -> 3 tokens.
        assertEquals(0, balance().compareTo(BigDecimal.valueOf(997)));
    }

    @Test
    @DisplayName("GET /instructions streams the (C, app, M) instruction for the metered action")
    void instructionsStreamManagedClient() {
        client.get().uri(b -> b.path(BASE + "/instructions").queryParam("action", BillingActionKeys.USER).build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].billedClientCode").isEqualTo("MINT")
                .jsonPath("$[0].configClientCode").isEqualTo("CINT")
                .jsonPath("$[0].appCode").isEqualTo("intapp");
    }

    @Test
    @DisplayName("GET /ai-allowed is true for an active wallet")
    void aiAllowedTrueForActiveWallet() {
        client.get().uri(b -> b.path(BASE + "/ai-allowed")
                .queryParam("appCode", "intapp").queryParam("clientCode", "MINT").build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(Boolean.class).isEqualTo(Boolean.TRUE);
    }

    @Test
    @DisplayName("GET /serving-status returns ACTIVE when the config resolves and the wallet is active")
    void servingStatusActive() {
        client.get().uri(b -> b.path(BASE + "/serving-status")
                .queryParam("urlAppCode", "intapp").queryParam("urlClientCode", "CINT")
                .queryParam("clientId", mClient.toBigInteger().toString()).build())
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("POST /charge-ai inside the monthly free grant debits nothing but records the usage")
    void aiChargeWithinGrantIsFreeButRecorded() {
        seedAiRates(new BigDecimal("1000"), new BigDecimal("1000000"));

        chargeAi("ai-free-1", new BigDecimal("600000"))
                .jsonPath("$.charged").isEqualTo(false);

        // Nothing debited...
        assertEquals(0, balance().compareTo(BigDecimal.valueOf(1000)));
        // ...but the call is on the ledger at zero tokens, so the grant depletes.
        assertEquals(0, aiQuantityThisMonth().compareTo(new BigDecimal("600000")));
        assertEquals(0, aiTokensThisMonth().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("POST /charge-ai splits the call that straddles the grant boundary")
    void aiChargeStraddlingGrantBillsOnlyTheExcess() {
        seedAiRates(new BigDecimal("1000"), new BigDecimal("1000000"));

        chargeAi("ai-split-1", new BigDecimal("600000")).jsonPath("$.charged").isEqualTo(false);
        // 600k already used, 400k of grant left: only the last 200k of this call is billable.
        chargeAi("ai-split-2", new BigDecimal("600000")).jsonPath("$.charged").isEqualTo(true);

        // 200,000 weighted * 1000 / 1M = 200 tokens.
        assertEquals(0, balance().compareTo(BigDecimal.valueOf(800)));

        // Grant spent, so the next call is billed whole: 500k * 1000 / 1M = 500.
        chargeAi("ai-split-3", new BigDecimal("500000")).jsonPath("$.charged").isEqualTo(true);
        assertEquals(0, balance().compareTo(BigDecimal.valueOf(300)));
    }

    @Test
    @DisplayName("POST /charge-ai is idempotent per requestId on both the billed and the free path")
    void aiChargeIsIdempotent() {
        seedAiRates(new BigDecimal("1000"), new BigDecimal("100000"));

        // Free path replayed: the second call must not add a second usage row, or the
        // grant would deplete twice as fast on any retry.
        chargeAi("ai-idem-free", new BigDecimal("50000"));
        chargeAi("ai-idem-free", new BigDecimal("50000"));
        assertEquals(0, aiQuantityThisMonth().compareTo(new BigDecimal("50000")));

        // Billed path replayed: 150k used total, 100k granted -> 50k billable -> 50 tokens.
        chargeAi("ai-idem-paid", new BigDecimal("100000"));
        chargeAi("ai-idem-paid", new BigDecimal("100000"));
        assertEquals(0, balance().compareTo(BigDecimal.valueOf(950)));
    }

    @Test
    @DisplayName("POST /charge-ai with no billing config for the app charges nothing")
    void aiChargeWithoutConfigIsNoCharge() {
        // The app has a config row, but no AI rate on it: the AI dimension is simply
        // not sold, so usage is free rather than an error.
        chargeAi("ai-norate-1", new BigDecimal("5000000"))
                .jsonPath("$.charged").isEqualTo(false);
        assertEquals(0, balance().compareTo(BigDecimal.valueOf(1000)));
    }

    private org.springframework.test.web.reactive.server.WebTestClient.BodyContentSpec chargeAi(
            String requestId, BigDecimal weightedTokens) {
        return client.post().uri(BASE + "/charge-ai")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AiChargeRequest("MINT", "intapp", "claude-opus-5", weightedTokens, requestId, "sess"))
                .exchange()
                .expectStatus().isOk()
                .expectBody();
    }

    /** Put AI pricing on the (cClient, appId) config row seeded in setUp. */
    private void seedAiRates(BigDecimal ratePerMillion, BigDecimal freePerMonth) {
        databaseClient.sql("UPDATE security_app_billing_config "
                + "SET AI_TOKENS_PER_MILLION = :rate, FREE_AI_TOKENS_PER_MONTH = :free "
                + "WHERE CLIENT_ID = :c AND APP_ID = :app")
                .bind("rate", ratePerMillion).bind("free", freePerMonth)
                .bind("c", cClient.longValue()).bind("app", appId.longValue())
                .then().block();
    }

    private BigDecimal aiSumThisMonth(String column) {
        LocalDate today = LocalDate.now();
        return databaseClient.sql("SELECT COALESCE(SUM(" + column + "), 0) AS total "
                + "FROM security_wallet_transaction WHERE ACTION_KEY = :key AND CHARGE_DATE BETWEEN :from AND :to")
                .bind("key", BillingActionKeys.AI_LLM_TOKENS)
                .bind("from", today.withDayOfMonth(1))
                .bind("to", YearMonth.from(today).atEndOfMonth())
                .map(row -> row.get("total", BigDecimal.class))
                .one().block();
    }

    private BigDecimal aiQuantityThisMonth() {
        return aiSumThisMonth("QUANTITY");
    }

    private BigDecimal aiTokensThisMonth() {
        return aiSumThisMonth("TOKENS");
    }

    private BigDecimal balance() {
        return walletDAO.findByClientAndApp(mClient, appId).map(w -> w.getBalance()).block();
    }

    private void seedUserConfig(ULong c, ULong app, BigDecimal userRate) {
        databaseClient.sql(
                "INSERT INTO security_app_billing_config (CLIENT_ID, APP_ID, USER_TOKENS_PER_MONTH, STATUS) "
                        + "VALUES (:c, :app, :rate, 'ACTIVE')")
                .bind("c", c.longValue()).bind("app", app.longValue()).bind("rate", userRate)
                .then().block();
    }
}
