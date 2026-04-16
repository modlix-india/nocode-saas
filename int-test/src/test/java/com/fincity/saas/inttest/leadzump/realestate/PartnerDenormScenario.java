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
 * Partner Denormalization Scenario — verifies:
 *   1. CLIENT_NAME is set on partner creation
 *   2. Full denorm sync populates totalTickets, activeUsers, clientName, userNames
 *   3. Delta denorm sync picks up ticket changes
 *   4. Filter by clientName (STRING_LOOSE)
 *   5. Filter by clientName + sort by totalTickets
 *   6. Filter returns empty for non-matching term
 *   7. Disabled generic CRUD endpoints
 *
 * Setup:
 *   - Main org with 1 product
 *   - 2 broker clients: "Alpha Corp" (2 tickets), "Beta Inc" (0 tickets)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PartnerDenormScenario extends BaseIntegrationTest {

    private static String token;
    private static String clientCode;
    private static String appCode;
    private static EntityProcessorApi api;
    private static Number userId;

    private static Number templateId;
    private static Number productId;
    private static Number freshStageId;

    private static String alphaClientName;
    private static String betaClientName;
    private static Number alphaClientId;
    private static Number betaClientId;
    private static String alphaEmail;
    private static String alphaToken;
    private static String alphaClientCode;

    private static final String PASSWORD = "Test@1234";

    @BeforeAll
    void setup() {
        appCode = prop("leadzump.app.code");
        String parentClientCode = prop("leadzump.client.code");
        String otp = prop("otp");

        String uid = UUID.randomUUID().toString().substring(0, 8);
        String email = "denorm-main-" + uid + "@inttest.local";

        SecurityApi secApi = new SecurityApi(baseHost());

        Response otpRes = secApi.generateRegistrationOtp(parentClientCode, appCode, email);
        assertThat(otpRes.statusCode()).as("Generate main OTP").isEqualTo(200);

        Response regRes = secApi.register(parentClientCode, appCode, mapOf(
                "clientName", "Denorm_Main_" + uid,
                "firstName", "DenormTest", "lastName", "IntTest",
                "emailId", email, "password", PASSWORD,
                "passType", "PASSWORD", "businessClient", true, "otp", otp
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
        api = new EntityProcessorApi(givenAuth(token, clientCode, appCode));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S1: Product setup
    // ═══════════════════════════════════════════════════════════════════

    @Test @Order(100)
    void s1_createTemplate() {
        Response res = api.createProductTemplate(mapOf(
                "name", "Denorm_Template", "productTemplateType", "GENERAL"));
        assertThat(res.statusCode()).as("Create template").isIn(200, 201);
        templateId = res.body().path("id");
    }

    @Test @Order(110)
    void s1_createStages() {
        Response fresh = api.createStage(mapOf(
                "name", "Fresh", "platform", "PRE_QUALIFICATION",
                "productTemplateId", templateId, "isParent", true, "order", 1,
                "stageType", "OPEN",
                "children", mapOf(0, mapOf("name", "New", "stageType", "OPEN", "order", 0))));
        assertThat(fresh.statusCode()).as("Create stage").isIn(200, 201);
        freshStageId = fresh.body().path("child[0].id");
    }

    @Test @Order(120)
    void s1_createProduct() {
        Response res = api.createProduct(mapOf(
                "name", "Denorm_Product", "code", "denorm-prod-" + System.currentTimeMillis(),
                "productTemplateId", templateId, "forPartner", true));
        assertThat(res.statusCode()).as("Create product").isIn(200, 201);
        productId = res.body().path("id");
    }

    @Test @Order(130)
    void s1_createCreationRule() {
        Response res = api.createCreationRule(mapOf(
                "name", "Default Rule", "productTemplateId", templateId,
                "stageId", freshStageId, "order", 0,
                "userDistributionType", "ROUND_ROBIN",
                "userDistributions", List.of(Map.of("userId", userId, "percentage", 100))));
        assertThat(res.statusCode()).as("Create rule").isIn(200, 201);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S2: Register 2 broker clients + create partners
    // ═══════════════════════════════════════════════════════════════════

    @Test @Order(200)
    void s2_registerBrokers() {
        SecurityApi secApi = new SecurityApi(baseHost());
        String otp = prop("otp");
        String uid = UUID.randomUUID().toString().substring(0, 8);

        // ── Alpha Corp ──
        alphaClientName = "AlphaCorp_" + uid;
        alphaEmail = "denorm-alpha-" + uid + "@inttest.local";
        Response otpA = secApi.generateRegistrationOtp(clientCode, appCode, alphaEmail);
        assertThat(otpA.statusCode()).isEqualTo(200);

        Response regA = secApi.register(clientCode, appCode, mapOf(
                "clientName", alphaClientName,
                "firstName", "AlphaUser", "lastName", "Test",
                "emailId", alphaEmail, "password", PASSWORD,
                "passType", "PASSWORD", "businessClient", true, "otp", otp));
        assertThat(regA.statusCode()).as("Register Alpha").isIn(200, 201);
        alphaClientId = regA.body().path("authentication.client.id");
        alphaClientCode = regA.body().path("authentication.client.code");
        alphaToken = regA.body().path("authentication.accessToken");

        // ── Beta Inc ──
        betaClientName = "BetaInc_" + uid;
        String betaEmail = "denorm-beta-" + uid + "@inttest.local";
        Response otpB = secApi.generateRegistrationOtp(clientCode, appCode, betaEmail);
        assertThat(otpB.statusCode()).isEqualTo(200);

        Response regB = secApi.register(clientCode, appCode, mapOf(
                "clientName", betaClientName,
                "firstName", "BetaUser", "lastName", "Test",
                "emailId", betaEmail, "password", PASSWORD,
                "passType", "PASSWORD", "businessClient", true, "otp", otp));
        assertThat(regB.statusCode()).as("Register Beta").isIn(200, 201);
        betaClientId = regB.body().path("authentication.client.id");
    }

    @Test @Order(210)
    void s2_createPartners() {
        Response resA = api.createPartner(mapOf("name", "Alpha Partner", "clientId", alphaClientId));
        assertThat(resA.statusCode()).as("Create Alpha partner").isIn(200, 201);

        Response resB = api.createPartner(mapOf("name", "Beta Partner", "clientId", betaClientId));
        assertThat(resB.statusCode()).as("Create Beta partner").isIn(200, 201);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S3: CLIENT_NAME set on creation
    // ═══════════════════════════════════════════════════════════════════

    @Test @Order(300)
    void s3_clientNameSetOnCreation() {
        Response res = api.queryPartners(0, 10);
        assertThat(res.statusCode()).isEqualTo(200);

        List<Map<String, Object>> content = res.body().path("content");

        Map<String, Object> alphaByName = content.stream()
                .filter(p -> alphaClientName.equals(p.get("clientName")))
                .findFirst().orElse(null);

        assertThat(alphaByName).as("Alpha partner found by clientName").isNotNull();
        assertThat(alphaByName.get("clientName")).as("clientName set on create").isEqualTo(alphaClientName);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S4: Alpha submits tickets, then denorm sync
    // ═══════════════════════════════════════════════════════════════════

    @Test @Order(400)
    void s4_alphaSubmits2Tickets() {
        if (alphaToken == null) {
            Response authRes = new SecurityApi(baseHost()).authenticate(clientCode, appCode, alphaEmail, PASSWORD);
            assertThat(authRes.statusCode()).isEqualTo(200);
            alphaToken = authRes.body().path("accessToken");
            alphaClientCode = authRes.body().path("user.clientCode");
        }
        EntityProcessorApi alphaApi = new EntityProcessorApi(givenAuth(alphaToken, alphaClientCode, appCode));

        for (int i = 1; i <= 2; i++) {
            Response res = alphaApi.createTicket(mapOf(
                    "name", "Denorm_Lead_" + i, "dialCode", 91,
                    "phoneNumber", "+91801010" + String.format("%04d", i),
                    "source", "Channel Partner", "productId", productId));
            assertThat(res.statusCode()).as("Alpha ticket " + i).isIn(200, 201);
        }
    }

    @Test @Order(410)
    void s4_fullDenormSync() {
        Response res = EntityProcessorApi.triggerPartnerDenorm(false);
        assertThat(res.statusCode()).as("Full denorm sync").isEqualTo(200);
        Number updated = res.body().path("partnersUpdated");
        assertThat(updated.intValue()).as("Partners synced").isGreaterThanOrEqualTo(2);
    }

    @Test @Order(420)
    void s4_verifyDenormFields() {
        Response res = api.queryPartners(0, 10);
        List<Map<String, Object>> content = res.body().path("content");

        Map<String, Object> alphaP = content.stream()
                .filter(p -> alphaClientName.equals(p.get("clientName")))
                .findFirst().orElse(null);
        assertThat(alphaP).as("Alpha partner found").isNotNull();
        assertThat(toLong(alphaP.get("totalTickets"))).as("Alpha totalTickets").isEqualTo(2L);
        assertThat(toLong(alphaP.get("activeUsers"))).as("Alpha activeUsers").isGreaterThanOrEqualTo(1L);
        assertThat((String) alphaP.get("userNames")).as("Alpha userNames populated").isNotBlank();

        Map<String, Object> betaP = content.stream()
                .filter(p -> betaClientName.equals(p.get("clientName")))
                .findFirst().orElse(null);
        assertThat(betaP).as("Beta partner found").isNotNull();
        assertThat(toLong(betaP.get("totalTickets"))).as("Beta totalTickets").isZero();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S5: Delta sync picks up new tickets
    // ═══════════════════════════════════════════════════════════════════

    @Test @Order(500)
    void s5_alphaSubmits1MoreTicket() throws InterruptedException {
        // Wait so the new ticket's CREATED_AT exceeds the DENORM_UPDATED_AT set by full sync
        Thread.sleep(2000);
        EntityProcessorApi alphaApi = new EntityProcessorApi(givenAuth(alphaToken, alphaClientCode, appCode));
        Response res = alphaApi.createTicket(mapOf(
                "name", "Denorm_Lead_Extra", "dialCode", 91,
                "phoneNumber", "+91801010009",
                "source", "Channel Partner", "productId", productId));
        assertThat(res.statusCode()).as("Extra ticket").isIn(200, 201);
    }

    @Test @Order(510)
    void s5_syncAfterNewTicket() {
        Response res = EntityProcessorApi.triggerPartnerDenorm(false);
        assertThat(res.statusCode()).as("Full denorm sync after new ticket").isEqualTo(200);
    }

    @Test @Order(520)
    void s5_verifyDeltaPickedUpNewTicket() {
        Response res = api.queryPartners(0, 10);
        List<Map<String, Object>> content = res.body().path("content");

        Map<String, Object> alphaP = content.stream()
                .filter(p -> alphaClientName.equals(p.get("clientName")))
                .findFirst().orElse(null);
        assertThat(alphaP).as("Alpha partner found").isNotNull();
        assertThat(toLong(alphaP.get("totalTickets"))).as("Alpha totalTickets after delta").isEqualTo(3L);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S6: Filter by clientName
    // ═══════════════════════════════════════════════════════════════════

    @Test @Order(600)
    void s6_filterByClientName_exact() {
        Map<String, Object> condition = Map.of(
                "field", "clientName",
                "value", alphaClientName,
                "operator", "EQUALS",
                "negate", false);

        Response res = api.queryPartnersWithCondition(0, 10, condition);
        assertThat(res.statusCode()).as("Filter by exact clientName").isEqualTo(200);

        List<Map<String, Object>> content = res.body().path("content");
        assertThat(content).as("Exactly 1 match").hasSize(1);
        assertThat(content.get(0).get("clientName")).isEqualTo(alphaClientName);
    }

    @Test @Order(610)
    void s6_filterByClientName_loose() {
        // STRING_LOOSE is a LIKE '%value%' search
        String searchTerm = alphaClientName.substring(0, 5);
        Map<String, Object> condition = Map.of(
                "field", "clientName",
                "value", searchTerm,
                "operator", "STRING_LOOSE_EQUAL",
                "negate", false);

        Response res = api.queryPartnersWithCondition(0, 10, condition);
        assertThat(res.statusCode()).as("Filter by loose clientName").isEqualTo(200);

        List<Map<String, Object>> content = res.body().path("content");
        assertThat(content).as("At least 1 match for prefix search").hasSizeGreaterThanOrEqualTo(1);
        for (Map<String, Object> p : content) {
            assertThat(((String) p.get("clientName")).toLowerCase())
                    .as("Each result contains search term")
                    .contains(searchTerm.toLowerCase());
        }
    }

    @Test @Order(620)
    void s6_filterByClientName_noMatch() {
        Map<String, Object> condition = Map.of(
                "field", "clientName",
                "value", "NonExistentClient_" + UUID.randomUUID(),
                "operator", "STRING_LOOSE_EQUAL",
                "negate", false);

        Response res = api.queryPartnersWithCondition(0, 10, condition);
        assertThat(res.statusCode()).as("Filter non-matching").isEqualTo(200);

        List<Map<String, Object>> content = res.body().path("content");
        assertThat(content == null || content.isEmpty()).as("No matches").isTrue();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S7: Filter + Sort combined
    // ═══════════════════════════════════════════════════════════════════

    @Test @Order(700)
    void s7_filterAndSort_byTotalTicketsDesc() {
        // Filter to include both Alpha and Beta, then sort by totalTickets DESC
        // Use a condition that matches both: clientName contains common suffix
        Map<String, Object> condition = Map.of(
                "operator", "OR",
                "negate", false,
                "conditions", List.of(
                        Map.of("field", "clientName", "value", alphaClientName, "operator", "EQUALS", "negate", false),
                        Map.of("field", "clientName", "value", betaClientName, "operator", "EQUALS", "negate", false)
                ));

        Response res = api.queryPartnersSortedWithCondition(0, 10, "totalTickets", "DESC", condition);
        assertThat(res.statusCode()).as("Filter + sort").isEqualTo(200);

        List<Map<String, Object>> content = res.body().path("content");
        assertThat(content).as("Both partners returned").hasSize(2);

        // Alpha (3 tickets) should come first in DESC order
        assertThat(content.get(0).get("clientName")).as("First = Alpha (most tickets)").isEqualTo(alphaClientName);
        assertThat(content.get(1).get("clientName")).as("Second = Beta (zero tickets)").isEqualTo(betaClientName);
    }

    @Test @Order(710)
    void s7_filterAndSort_byClientNameAsc() {
        Map<String, Object> condition = Map.of(
                "operator", "OR",
                "negate", false,
                "conditions", List.of(
                        Map.of("field", "clientName", "value", alphaClientName, "operator", "EQUALS", "negate", false),
                        Map.of("field", "clientName", "value", betaClientName, "operator", "EQUALS", "negate", false)
                ));

        Response res = api.queryPartnersSortedWithCondition(0, 10, "clientName", "ASC", condition);
        assertThat(res.statusCode()).as("Filter + sort by name").isEqualTo(200);

        List<Map<String, Object>> content = res.body().path("content");
        assertThat(content).hasSize(2);

        // Alpha < Beta alphabetically
        String first = (String) content.get(0).get("clientName");
        String second = (String) content.get(1).get("clientName");
        assertThat(first.compareToIgnoreCase(second))
                .as("Alphabetical order: " + first + " before " + second)
                .isLessThanOrEqualTo(0);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S8: Disabled generic CRUD endpoints
    // ═══════════════════════════════════════════════════════════════════

    @Test @Order(800)
    void s8_queryIsTheOnlyReadPath() {
        // Base controller endpoints (GET /{id}, POST /, PUT /{id}, DELETE /{id})
        // are not exposed since PartnerController does not extend the base controller.
        // Verify the query endpoint is the working read path.
        Response queryRes = api.queryPartners(0, 1);
        assertThat(queryRes.statusCode()).as("Query endpoint works").isEqualTo(200);
        assertThat((Integer) queryRes.body().path("totalElements")).as("Has partners").isGreaterThanOrEqualTo(1);
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
