package com.fincity.saas.inttest.leadzump.realestate;

import com.fincity.saas.inttest.base.BaseIntegrationTest;
import com.fincity.saas.inttest.base.EntityProcessorApi;
import com.fincity.saas.inttest.base.SecurityApi;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Partner Client Sort Scenario — verifies that sort by denormalized fields
 * (totalTickets, activeUsers, clientName) is applied at SQL level with proper
 * cross-page ordering.
 *
 * Setup:
 *   - Main org (builder) with 1 product
 *   - 3 broker clients registered as sub-clients (levelType=BP)
 *   - 3 partners created pointing to the 3 broker clients
 *   - Broker A authenticates and creates 3 tickets → totalTickets = 3
 *   - Broker B authenticates and creates 1 ticket  → totalTickets = 1
 *   - Broker C creates no tickets               → totalTickets = 0
 *   - Full denorm sync to populate denormalized fields
 *
 * Key assertion (cross-page sort):
 *   With page size 2 and sort=totalTickets,DESC:
 *     page 0 → [A(3), B(1)]
 *     page 1 → [C(0)]
 *   The last item on page 0 must have totalTickets >= first item on page 1.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PartnerClientSortScenario extends BaseIntegrationTest {

    // ── Main org ───────────────────────────────────────────────────────
    private static String token;
    private static String clientCode;
    private static String appCode;
    private static EntityProcessorApi api;
    private static Number userId;

    // ── Product setup ──────────────────────────────────────────────────
    private static Number templateId;
    private static Number productId;
    private static Number freshStageId;

    // ── Broker A — 3 tickets ───────────────────────────────────────────
    private static String brokerAEmail;
    private static String brokerAToken;
    private static String brokerAClientCode;
    private static Number brokerAClientId;

    // ── Broker B — 1 ticket ────────────────────────────────────────────
    private static String brokerBEmail;
    private static String brokerBToken;
    private static String brokerBClientCode;
    private static Number brokerBClientId;

    // ── Broker C — 0 tickets ───────────────────────────────────────────
    private static Number brokerCClientId;

    private static final String PASSWORD = "Test@1234";

    // ═══════════════════════════════════════════════════════════════════
    //  Setup — register main org
    // ═══════════════════════════════════════════════════════════════════

    @BeforeAll
    void setup() {
        appCode = prop("leadzump.app.code");
        String parentClientCode = prop("leadzump.client.code");
        String otp = prop("otp");

        String uid = UUID.randomUUID().toString().substring(0, 8);
        String email = "sort-main-" + uid + "@inttest.local";

        SecurityApi secApi = new SecurityApi(baseHost());

        Response otpRes = secApi.generateRegistrationOtp(parentClientCode, appCode, email);
        assertThat(otpRes.statusCode()).as("Generate main OTP").isEqualTo(200);

        Response regRes = secApi.register(parentClientCode, appCode, mapOf(
                "clientName", "Sort_Main_" + uid,
                "firstName", "SortTest",
                "lastName", "IntTest",
                "emailId", email,
                "password", PASSWORD,
                "passType", "PASSWORD",
                "businessClient", true,
                "otp", otp
        ));
        assertThat(regRes.statusCode()).as("Register main org").isIn(200, 201);

        token = regRes.body().path("authentication.accessToken");
        clientCode = regRes.body().path("authentication.client.code");
        userId = regRes.body().path("authentication.user.id");
        if (clientCode == null || clientCode.isBlank()) {
            Response authRes = secApi.authenticate(parentClientCode, appCode, email, PASSWORD);
            assertThat(authRes.statusCode()).isEqualTo(200);
            token = authRes.body().path("accessToken");
            clientCode = authRes.body().path("user.clientCode");
            userId = authRes.body().path("user.id");
        }

        assertThat(token).as("Main org token").isNotBlank();
        assertThat(clientCode).as("Main org clientCode").isNotBlank();

        api = new EntityProcessorApi(givenAuth(token, clientCode, appCode));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S1: Minimal product setup
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(100)
    void s1_01_createTemplate() {
        Response res = api.createProductTemplate(mapOf(
                "name", "SortTest_Template",
                "productTemplateType", "GENERAL"
        ));
        assertThat(res.statusCode()).as("Create template").isIn(200, 201);
        templateId = res.body().path("id");
        assertThat(templateId).isNotNull();
    }

    @Test
    @Order(110)
    void s1_02_createStages() {
        Response fresh = api.createStage(mapOf(
                "name", "Fresh",
                "platform", "PRE_QUALIFICATION",
                "productTemplateId", templateId,
                "isParent", true,
                "order", 1,
                "stageType", "OPEN",
                "children", mapOf(0, mapOf("name", "New", "stageType", "OPEN", "order", 0))
        ));
        assertThat(fresh.statusCode()).as("Create Fresh stage").isIn(200, 201);
        freshStageId = fresh.body().path("child[0].id");

        Response booking = api.createStage(mapOf(
                "name", "Booking",
                "platform", "POST_QUALIFICATION",
                "productTemplateId", templateId,
                "isParent", true,
                "order", 10,
                "stageType", "CLOSED",
                "isSuccess", true,
                "isFailure", false,
                "children", mapOf(0, mapOf("name", "Booking Done", "stageType", "CLOSED", "order", 0))
        ));
        assertThat(booking.statusCode()).as("Create Booking stage").isIn(200, 201);
    }

    @Test
    @Order(120)
    void s1_03_createProduct() {
        Response res = api.createProduct(mapOf(
                "name", "SortTest_Product",
                "code", "sort-prod-" + System.currentTimeMillis(),
                "productTemplateId", templateId,
                "forPartner", true
        ));
        assertThat(res.statusCode()).as("Create product").isIn(200, 201);
        productId = res.body().path("id");
        assertThat(productId).isNotNull();
    }

    @Test
    @Order(130)
    void s1_04_createCreationRule() {
        assertThat(freshStageId).as("Fresh stage child ID must be set").isNotNull();
        assertThat(userId).as("Main org userId must be set").isNotNull();
        Response res = api.createCreationRule(mapOf(
                "name", "Default Rule",
                "productTemplateId", templateId,
                "stageId", freshStageId,
                "order", 0,
                "userDistributionType", "ROUND_ROBIN",
                "userDistributions", List.of(Map.of("userId", userId, "percentage", 100))
        ));
        assertThat(res.statusCode()).as("Create creation rule").isIn(200, 201);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S2: Register 3 broker clients + create 3 partners
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(200)
    void s2_01_registerBrokerClients() {
        SecurityApi secApi = new SecurityApi(baseHost());
        String otp = prop("otp");

        // ── Broker A ──
        String uidA = UUID.randomUUID().toString().substring(0, 8);
        brokerAEmail = "sort-bkA-" + uidA + "@inttest.local";
        Response otpA = secApi.generateRegistrationOtp(clientCode, appCode, brokerAEmail);
        assertThat(otpA.statusCode()).as("Broker A OTP").isEqualTo(200);

        Response regA = secApi.register(clientCode, appCode, mapOf(
                "clientName", "Sort_BrokerA_" + uidA,
                "firstName", "BrokerA", "lastName", "IntTest",
                "emailId", brokerAEmail, "password", PASSWORD,
                "passType", "PASSWORD", "businessClient", true, "otp", otp
        ));
        assertThat(regA.statusCode()).as("Register broker A").isIn(200, 201);
        brokerAClientId = regA.body().path("authentication.client.id");
        brokerAClientCode = regA.body().path("authentication.client.code");
        brokerAToken = regA.body().path("authentication.accessToken");
        assertThat(brokerAClientId).as("Broker A client ID").isNotNull();

        // ── Broker B ──
        String uidB = UUID.randomUUID().toString().substring(0, 8);
        brokerBEmail = "sort-bkB-" + uidB + "@inttest.local";
        Response otpB = secApi.generateRegistrationOtp(clientCode, appCode, brokerBEmail);
        assertThat(otpB.statusCode()).as("Broker B OTP").isEqualTo(200);

        Response regB = secApi.register(clientCode, appCode, mapOf(
                "clientName", "Sort_BrokerB_" + uidB,
                "firstName", "BrokerB", "lastName", "IntTest",
                "emailId", brokerBEmail, "password", PASSWORD,
                "passType", "PASSWORD", "businessClient", true, "otp", otp
        ));
        assertThat(regB.statusCode()).as("Register broker B").isIn(200, 201);
        brokerBClientId = regB.body().path("authentication.client.id");
        brokerBClientCode = regB.body().path("authentication.client.code");
        brokerBToken = regB.body().path("authentication.accessToken");
        assertThat(brokerBClientId).as("Broker B client ID").isNotNull();

        // ── Broker C ──
        String uidC = UUID.randomUUID().toString().substring(0, 8);
        String brokerCEmail = "sort-bkC-" + uidC + "@inttest.local";
        Response otpC = secApi.generateRegistrationOtp(clientCode, appCode, brokerCEmail);
        assertThat(otpC.statusCode()).as("Broker C OTP").isEqualTo(200);

        Response regC = secApi.register(clientCode, appCode, mapOf(
                "clientName", "Sort_BrokerC_" + uidC,
                "firstName", "BrokerC", "lastName", "IntTest",
                "emailId", brokerCEmail, "password", PASSWORD,
                "passType", "PASSWORD", "businessClient", true, "otp", otp
        ));
        assertThat(regC.statusCode()).as("Register broker C").isIn(200, 201);
        brokerCClientId = regC.body().path("authentication.client.id");
        assertThat(brokerCClientId).as("Broker C client ID").isNotNull();
    }

    @Test
    @Order(210)
    void s2_02_createPartners() {
        Response resA = api.createPartner(mapOf("name", "Sort Partner A", "clientId", brokerAClientId));
        assertThat(resA.statusCode()).as("Create partner A").isIn(200, 201);

        Response resB = api.createPartner(mapOf("name", "Sort Partner B", "clientId", brokerBClientId));
        assertThat(resB.statusCode()).as("Create partner B").isIn(200, 201);

        Response resC = api.createPartner(mapOf("name", "Sort Partner C", "clientId", brokerCClientId));
        assertThat(resC.statusCode()).as("Create partner C").isIn(200, 201);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S3: Brokers submit tickets
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(300)
    void s3_01_brokerA_submits_3_tickets() {
        if (brokerAToken == null || brokerAClientCode == null) {
            Response authRes = new SecurityApi(baseHost()).authenticate(clientCode, appCode, brokerAEmail, PASSWORD);
            assertThat(authRes.statusCode()).isEqualTo(200);
            brokerAToken = authRes.body().path("accessToken");
            brokerAClientCode = authRes.body().path("user.clientCode");
        }
        EntityProcessorApi brokerAApi = new EntityProcessorApi(
                givenAuth(brokerAToken, brokerAClientCode, appCode));

        for (int i = 1; i <= 3; i++) {
            Response res = brokerAApi.createTicket(mapOf(
                    "name", "Sort_Lead_A_" + i,
                    "dialCode", 91,
                    "phoneNumber", "+91901010" + String.format("%02d", i),
                    "source", "Channel Partner",
                    "productId", productId
            ));
            assertThat(res.statusCode()).as("Broker A ticket " + i).isIn(200, 201);
        }
    }

    @Test
    @Order(310)
    void s3_02_brokerB_submits_1_ticket() {
        if (brokerBToken == null || brokerBClientCode == null) {
            Response authRes = new SecurityApi(baseHost()).authenticate(clientCode, appCode, brokerBEmail, PASSWORD);
            assertThat(authRes.statusCode()).isEqualTo(200);
            brokerBToken = authRes.body().path("accessToken");
            brokerBClientCode = authRes.body().path("user.clientCode");
        }
        EntityProcessorApi brokerBApi = new EntityProcessorApi(
                givenAuth(brokerBToken, brokerBClientCode, appCode));

        Response res = brokerBApi.createTicket(mapOf(
                "name", "Sort_Lead_B_1",
                "dialCode", 91,
                "phoneNumber", "+91902020001",
                "source", "Channel Partner",
                "productId", productId
        ));
        assertThat(res.statusCode()).as("Broker B ticket 1").isIn(200, 201);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S3b: Trigger full denorm sync to populate denormalized fields
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(350)
    void s3b_triggerFullDenormSync() {
        Response res = EntityProcessorApi.triggerPartnerDenorm(false);
        assertThat(res.statusCode()).as("Full denorm sync").isEqualTo(200);
        Number updated = res.body().path("partnersUpdated");
        assertThat(updated).as("Partners updated").isNotNull();
        assertThat(updated.intValue()).as("At least 3 partners synced").isGreaterThanOrEqualTo(3);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S4: Sort assertions — using denormalized Partner fields
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(400)
    void s4_01_basicList_allPartnersPresent() {
        Response res = api.queryPartners(0, 10);
        assertThat(res.statusCode()).as("Basic list").isEqualTo(200);
        assertThat((Integer) res.body().path("totalElements")).as("All 3 partners visible").isGreaterThanOrEqualTo(3);
    }

    @Test
    @Order(410)
    void s4_02_sortByTotalTickets_desc_crossPageOrdering() {
        Response page0Res = api.queryPartnersSorted(0, 2, "totalTickets", "DESC");
        assertThat(page0Res.statusCode()).as("Sort totalTickets DESC — page 0").isEqualTo(200);
        List<Map<String, Object>> page0 = page0Res.body().path("content");
        assertThat(page0).as("Page 0 must have 2 items").hasSize(2);

        Response page1Res = api.queryPartnersSorted(1, 2, "totalTickets", "DESC");
        assertThat(page1Res.statusCode()).as("Sort totalTickets DESC — page 1").isEqualTo(200);
        List<Map<String, Object>> page1 = page1Res.body().path("content");
        assertThat(page1).as("Page 1 must have at least 1 item").isNotEmpty();

        int totalElements = page0Res.body().path("totalElements");
        assertThat(totalElements).as("Total elements").isGreaterThanOrEqualTo(3);

        long lastOnPage0 = toLong(page0.get(page0.size() - 1).get("totalTickets"));
        long firstOnPage1 = toLong(page1.get(0).get("totalTickets"));
        assertThat(lastOnPage0)
                .as("Cross-page: last on page 0 >= first on page 1 (proves SQL-level sort)")
                .isGreaterThanOrEqualTo(firstOnPage1);
    }

    @Test
    @Order(420)
    void s4_03_sortByTotalTickets_desc_exactOrdering() {
        Response page0Res = api.queryPartnersSorted(0, 2, "totalTickets", "DESC");
        List<Map<String, Object>> page0 = page0Res.body().path("content");

        long first = toLong(page0.get(0).get("totalTickets"));
        long second = toLong(page0.get(1).get("totalTickets"));
        assertThat(first).as("1st partner (most tickets)").isEqualTo(3L);
        assertThat(second).as("2nd partner").isEqualTo(1L);

        Response page1Res = api.queryPartnersSorted(1, 2, "totalTickets", "DESC");
        List<Map<String, Object>> page1 = page1Res.body().path("content");
        assertThat(page1).hasSize(1);
        assertThat(toLong(page1.get(0).get("totalTickets"))).as("Last partner (zero tickets)").isEqualTo(0L);
    }

    @Test
    @Order(430)
    void s4_04_sortByTotalTickets_asc() {
        Response res = api.queryPartnersSorted(0, 10, "totalTickets", "ASC");
        assertThat(res.statusCode()).as("Sort totalTickets ASC").isEqualTo(200);
        List<Map<String, Object>> content = res.body().path("content");
        assertThat(content).hasSizeGreaterThanOrEqualTo(3);

        for (int i = 0; i < content.size() - 1; i++) {
            long cur = toLong(content.get(i).get("totalTickets"));
            long next = toLong(content.get(i + 1).get("totalTickets"));
            assertThat(cur).as("ASC order violation at index " + i).isLessThanOrEqualTo(next);
        }
    }

    @Test
    @Order(440)
    void s4_05_sortByActiveUsers_desc() {
        Response res = api.queryPartnersSorted(0, 10, "activeUsers", "DESC");
        assertThat(res.statusCode()).as("Sort activeUsers DESC").isEqualTo(200);

        List<Map<String, Object>> content = res.body().path("content");
        assertThat(content).as("Should return all 3 partners").hasSizeGreaterThanOrEqualTo(3);

        for (int i = 0; i < content.size() - 1; i++) {
            long cur = toLong(content.get(i).get("activeUsers"));
            long next = toLong(content.get(i + 1).get("activeUsers"));
            assertThat(cur).as("DESC order violation at index " + i).isGreaterThanOrEqualTo(next);
        }
    }

    @Test
    @Order(450)
    void s4_06_sortByClientName_asc() {
        Response res = api.queryPartnersSorted(0, 10, "clientName", "ASC");
        assertThat(res.statusCode()).as("Sort clientName ASC").isEqualTo(200);

        List<Map<String, Object>> content = res.body().path("content");
        assertThat(content).hasSizeGreaterThanOrEqualTo(3);

        for (int i = 0; i < content.size() - 1; i++) {
            String cur = (String) content.get(i).get("clientName");
            String next = (String) content.get(i + 1).get("clientName");
            assertThat(cur).as("clientName present").isNotNull();
            if (next != null) {
                assertThat(cur.compareToIgnoreCase(next))
                        .as("ASC order violation at index " + i + ": " + cur + " vs " + next)
                        .isLessThanOrEqualTo(0);
            }
        }
    }

    @Test
    @Order(460)
    void s4_07_denormFieldsPopulated() {
        Response res = api.queryPartners(0, 10);
        List<Map<String, Object>> content = res.body().path("content");

        for (Map<String, Object> partner : content) {
            assertThat(partner.get("clientName")).as("clientName populated").isNotNull();
            assertThat(partner.get("totalTickets")).as("totalTickets populated").isNotNull();
            assertThat(partner.get("activeUsers")).as("activeUsers populated").isNotNull();
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private static long toLong(Object value) {
        if (value == null) return 0L;
        return ((Number) value).longValue();
    }

    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> mapOf(Object... keyValues) {
        Map<Object, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) map.put(keyValues[i], keyValues[i + 1]);
        return (Map<K, V>) map;
    }
}
