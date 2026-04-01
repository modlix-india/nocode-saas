package com.fincity.saas.inttest.leadzump.realestate;

import com.fincity.saas.inttest.base.BaseIntegrationTest;
import com.fincity.saas.inttest.base.EntityProcessorApi;
import com.fincity.saas.inttest.base.ProfileHelper;
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
 * Channel Partner with Dual Client Managers — Ticket Duplication Bug.
 *
 * Reproduces a bug where a channel partner (CP) that has TWO client managers
 * assigned causes duplicate tickets to be created when the CP submits a lead.
 *
 * <h3>Test Setup:</h3>
 * <ul>
 *   <li>Builder admin registers, creates template/stages/product/creation-rule</li>
 *   <li>Two team members invited as Sales Members (Manager1, Manager2)</li>
 *   <li>A channel partner (broker) registers as a sub-client</li>
 *   <li>Both Manager1 and Manager2 are assigned as client managers of the CP</li>
 * </ul>
 *
 * <h3>Test Scenarios:</h3>
 * <ul>
 *   <li>S1 (300-310): CP submits a lead — verify exactly ONE ticket created, not two</li>
 *   <li>S2 (400-420): CP submits a second lead with a different phone — verify one ticket each</li>
 *   <li>S3 (500-510): Builder queries all tickets — total count matches expected (no duplicates)</li>
 * </ul>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CPDualManagerDedupScenario extends BaseIntegrationTest {

    // Builder admin
    private static String token, clientCode, appCode, parentClientCode;
    private static Number userId;
    private static EntityProcessorApi api;

    // Template and stages
    private static Number templateId;
    private static Number stageFreshId, stageOpenId;

    // Product
    private static Number productId;
    private static String productCode;

    // Team members (will be dual client managers of the CP)
    private static Number manager1UserId, manager2UserId;

    // Channel Partner (broker)
    private static String cpToken, cpEmail;
    private static Number cpClientId;
    private static EntityProcessorApi cpApi;

    // Ticket IDs
    private static Number cpTicket1Id, cpTicket2Id;

    // Shared UID for unique emails per run
    private static String uid;
    private static SecurityApi secApi;
    private static Number salesMemberProfileId;

    @BeforeAll
    void setup() {
        appCode = prop("leadzump.app.code");
        parentClientCode = prop("leadzump.client.code");

        uid = UUID.randomUUID().toString().substring(0, 8);
        String email = "re-dualmgr-" + uid + "@inttest.local";
        String password = "Test@1234";

        secApi = new SecurityApi(baseHost());
        String otp = prop("otp");

        Response otpRes = secApi.generateRegistrationOtp(parentClientCode, appCode, email);
        assertThat(otpRes.statusCode()).as("Generate OTP").isEqualTo(200);

        Response regRes = secApi.register(parentClientCode, appCode, mapOf(
                "clientName", "RE_DualMgr_" + uid,
                "firstName", "DualMgrBuilder",
                "lastName", "IntTest",
                "emailId", email,
                "password", password,
                "passType", "PASSWORD",
                "businessClient", true,
                "otp", otp
        ));

        assertThat(regRes.statusCode()).as("Self-registration").isIn(200, 201);

        clientCode = regRes.body().path("authentication.client.code");
        userId = regRes.body().path("authentication.user.id");

        // Re-authenticate to get a token with correct managed-client context.
        // The registration token has managedClientCode=SYSTEM which doesn't resolve
        // the user's actual client for invite/profile operations.
        Response authRes = secApi.authenticate(parentClientCode, appCode, email, password);
        assertThat(authRes.statusCode()).as("Post-registration auth").isEqualTo(200);
        token = authRes.body().path("accessToken");
        assertThat(token).as("Auth token").isNotNull().isNotEmpty();

        if (clientCode == null || clientCode.isBlank()) {
            clientCode = authRes.body().path("user.clientCode");
        }

        // Builder admin calls use SYSTEM in the URL path; only CP calls use CP's clientCode
        api = new EntityProcessorApi(givenAuth(token, parentClientCode, appCode));

        // Resolve profile IDs dynamically
        ProfileHelper profiles = ProfileHelper.load(secApi, token, parentClientCode, appCode);
        salesMemberProfileId = profiles.getByName("Sales Member");
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Setup: Template, Stages, Product, Creation Rule
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(100)
    void setup_createTemplateAndStages() {
        Response tmpl = api.createProductTemplate(Map.of(
                "name", "RE_DualMgrTest_Template",
                "description", "Dual client manager dedup test",
                "productTemplateType", "GENERAL"
        ));
        assertThat(tmpl.statusCode()).as("Create product template").isIn(200, 201);
        templateId = tmpl.body().path("id");
        assertThat(templateId).isNotNull();

        // Fresh -> Open (PRE_QUALIFICATION)
        Response fresh = api.createStage(mapOf(
                "name", "Fresh",
                "platform", "PRE_QUALIFICATION",
                "productTemplateId", templateId,
                "isParent", true,
                "order", 1,
                "stageType", "OPEN",
                "children", mapOf(
                        0, mapOf("name", "Open", "stageType", "OPEN", "order", 0)
                )
        ));
        assertThat(fresh.statusCode()).as("Create Fresh stage with Open child").isIn(200, 201);
        stageFreshId = fresh.body().path("parent.id");
        stageOpenId = fresh.body().path("child[0].id");
        assertThat(stageFreshId).as("Fresh parent stage ID").isNotNull();
        assertThat(stageOpenId).as("Open child stage ID").isNotNull();
    }

    @Test
    @Order(110)
    void setup_createProduct() {
        Response res = api.createProduct(mapOf(
                "name", "RE_DualMgrTest_Product",
                "description", "Dual manager dedup test product",
                "productTemplateId", templateId,
                "forPartner", true
        ));

        assertThat(res.statusCode()).as("Create product").isIn(200, 201);
        productId = res.body().path("id");
        productCode = res.body().path("code");
        assertThat(productId).isNotNull();
        assertThat(productCode).as("Product code").isNotNull().isNotEmpty();
    }

    @Test
    @Order(120)
    void setup_createCreationRule() {
        // Default creation rule: round-robin to builder admin
        Response res = api.createCreationRule(mapOf(
                "name", "Default DualMgr Rule",
                "productTemplateId", templateId,
                "stageId", stageOpenId,
                "order", 0,
                "userDistributionType", "ROUND_ROBIN",
                "userDistributions", List.of(Map.of(
                        "userId", userId,
                        "percentage", 100
                ))
        ));
        assertThat(res.statusCode()).as("Create creation rule").isIn(200, 201);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Setup: Invite Two Team Members (will become client managers)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(200)
    void setup_inviteManager1() {
        Response inv = secApi.inviteUser(token, parentClientCode, appCode, mapOf(
                "emailId", "dualmgr-m1-" + uid + "@inttest.local",
                "firstName", "Manager1", "lastName", "IntTest",
                "profileId", salesMemberProfileId, "reportingTo", userId
        ));
        assertThat(inv.statusCode()).as("Invite Manager1: " + inv.body().asString()).isIn(200, 201);
        String code = inv.body().path("userRequest.inviteCode");

        Response acc = secApi.acceptInvite(parentClientCode, appCode, mapOf(
                "emailId", "dualmgr-m1-" + uid + "@inttest.local",
                "firstName", "Manager1", "lastName", "IntTest",
                "password", "Test@1234", "passType", "PASSWORD", "inviteCode", code
        ));
        assertThat(acc.statusCode()).as("Accept Manager1").isIn(200, 201);
        manager1UserId = acc.body().path("authentication.user.id");
        assertThat(manager1UserId).as("Manager1 userId").isNotNull();
    }

    @Test
    @Order(210)
    void setup_inviteManager2() {
        Response inv = secApi.inviteUser(token, parentClientCode, appCode, mapOf(
                "emailId", "dualmgr-m2-" + uid + "@inttest.local",
                "firstName", "Manager2", "lastName", "IntTest",
                "profileId", salesMemberProfileId, "reportingTo", userId
        ));
        assertThat(inv.statusCode()).as("Invite Manager2").isIn(200, 201);
        String code = inv.body().path("userRequest.inviteCode");

        Response acc = secApi.acceptInvite(parentClientCode, appCode, mapOf(
                "emailId", "dualmgr-m2-" + uid + "@inttest.local",
                "firstName", "Manager2", "lastName", "IntTest",
                "password", "Test@1234", "passType", "PASSWORD", "inviteCode", code
        ));
        assertThat(acc.statusCode()).as("Accept Manager2").isIn(200, 201);
        manager2UserId = acc.body().path("authentication.user.id");
        assertThat(manager2UserId).as("Manager2 userId").isNotNull();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Setup: Register CP, Create Partner, Assign BOTH Client Managers
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(220)
    void setup_registerChannelPartner() {
        cpEmail = "dualmgr-cp-" + uid + "@inttest.local";

        // Builder admin registers the CP using their token — creates CP as sub-client of builder
        Response regRes = secApi.registerClient(token, parentClientCode, appCode, mapOf(
                "clientName", "DualMgr_CP_" + uid,
                "firstName", "CPAdmin", "lastName", "IntTest",
                "emailId", cpEmail, "password", "Test@1234",
                "passType", "PASSWORD",
                "businessClient", true
        ));
        assertThat(regRes.statusCode()).as("Register CP: " + regRes.body().asString()).isIn(200, 201);

        cpClientId = regRes.body().path("authentication.client.id");
        if (cpClientId == null) {
            cpClientId = regRes.body().path("client.id");
        }
        assertThat(cpClientId).as("CP client ID").isNotNull();
    }

    @Test
    @Order(230)
    void setup_createPartner() {
        Response res = api.createPartner(mapOf(
                "name", "DualMgr_Partner_" + uid,
                "clientId", cpClientId
        ));
        assertThat(res.statusCode()).as("Create partner for CP: " + res.body().asString()).isIn(200, 201);
    }

    @Test
    @Order(235)
    void setup_authenticateCP() {
        // CP authenticates using the BUILDER's client code in the URL
        // (must happen AFTER partner creation to establish client hierarchy link)
        Response cpAuthRes = secApi.authenticate(clientCode, appCode, cpEmail, "Test@1234");
        assertThat(cpAuthRes.statusCode()).as("CP auth: " + cpAuthRes.body().asString()).isEqualTo(200);
        cpToken = cpAuthRes.body().path("accessToken");

        assertThat(cpToken).as("CP token").isNotNull().isNotEmpty();

        // CP creates tickets using the BUILDER's client code in the URL
        cpApi = new EntityProcessorApi(givenAuth(cpToken, clientCode, appCode));
    }

    @Test
    @Order(240)
    void setup_assignBothClientManagers() {
        // Assign Manager1 as client manager of the CP
        Response r1 = secApi.assignClientManager(token, parentClientCode, appCode, manager1UserId, cpClientId);
        assertThat(r1.statusCode())
                .as("Assign Manager1 as client manager of CP: " + r1.body().asString())
                .isIn(200, 201);

        // Assign Manager2 as client manager of the SAME CP
        Response r2 = secApi.assignClientManager(token, parentClientCode, appCode, manager2UserId, cpClientId);
        assertThat(r2.statusCode())
                .as("Assign Manager2 as client manager of CP")
                .isIn(200, 201);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S1: CP submits a lead — must create exactly ONE ticket
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(300)
    void s1_01_cpCreatesLead_shouldCreateExactlyOneTicket() {
        Response res = cpApi.createTicket(mapOf(
                "name", "DualMgr_Lead1",
                "dialCode", 91,
                "phoneNumber", "+919100000001",
                "productId", productId,
                "source", "Channel Partner",
                "subSource", "DualMgrCP"
        ));

        assertThat(res.statusCode()).as("CP lead creation should succeed").isIn(200, 201);
        cpTicket1Id = res.body().path("id");
        assertThat(cpTicket1Id).as("CP ticket 1 ID").isNotNull();
    }

    @Test
    @Order(310)
    void s1_02_builderSeesExactlyOneTicketForThatPhone() {
        // Builder admin queries tickets — should see exactly ONE ticket with this phone
        assertThat(cpTicket1Id).as("CP ticket 1 must exist").isNotNull();

        Response res = api.queryTicketsEagerSorted(0, 100, "id", "DESC");
        assertThat(res.statusCode()).as("Builder query tickets").isEqualTo(200);

        List<Map<String, Object>> content = res.body().path("content");
        assertThat(content).as("Should have tickets").isNotNull().isNotEmpty();

        // Count tickets with phone +919100000001
        long matchingTickets = content.stream()
                .filter(t -> "+919100000001".equals(t.get("phoneNumber"))
                        || "9100000001".equals(String.valueOf(t.get("phoneNumber"))))
                .count();

        assertThat(matchingTickets)
                .as("BUG: CP with 2 client managers should create exactly 1 ticket, not duplicates")
                .isEqualTo(1);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S2: CP submits a second lead (different phone) — one ticket each
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(400)
    void s2_01_cpCreatesSecondLead() {
        Response res = cpApi.createTicket(mapOf(
                "name", "DualMgr_Lead2",
                "dialCode", 91,
                "phoneNumber", "+919100000002",
                "productId", productId,
                "source", "Channel Partner",
                "subSource", "DualMgrCP"
        ));

        assertThat(res.statusCode()).as("CP second lead should succeed").isIn(200, 201);
        cpTicket2Id = res.body().path("id");
        assertThat(cpTicket2Id).as("CP ticket 2 ID").isNotNull();
    }

    @Test
    @Order(410)
    void s2_02_ticketIdsAreDifferent() {
        assertThat(cpTicket1Id).as("Ticket 1 must exist").isNotNull();
        assertThat(cpTicket2Id).as("Ticket 2 must exist").isNotNull();
        assertThat(cpTicket2Id.longValue())
                .as("Second lead should get a different ticket ID")
                .isNotEqualTo(cpTicket1Id.longValue());
    }

    @Test
    @Order(420)
    void s2_03_eachPhoneHasExactlyOneTicket() {
        Response res = api.queryTicketsEagerSorted(0, 100, "id", "DESC");
        assertThat(res.statusCode()).as("Builder query tickets").isEqualTo(200);

        List<Map<String, Object>> content = res.body().path("content");
        assertThat(content).as("Should have tickets").isNotNull().isNotEmpty();

        // Count tickets per phone number
        long phone1Count = content.stream()
                .filter(t -> "+919100000001".equals(t.get("phoneNumber"))
                        || "9100000001".equals(String.valueOf(t.get("phoneNumber"))))
                .count();
        long phone2Count = content.stream()
                .filter(t -> "+919100000002".equals(t.get("phoneNumber"))
                        || "9100000002".equals(String.valueOf(t.get("phoneNumber"))))
                .count();

        assertThat(phone1Count)
                .as("BUG: Phone +919100000001 should have exactly 1 ticket (dual managers must not duplicate)")
                .isEqualTo(1);
        assertThat(phone2Count)
                .as("BUG: Phone +919100000002 should have exactly 1 ticket (dual managers must not duplicate)")
                .isEqualTo(1);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S3: Total ticket count — must match expected (no hidden duplicates)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(500)
    void s3_01_totalTicketCountMatchesExpected() {
        Response res = api.queryTicketsEagerSorted(0, 100, "id", "DESC");
        assertThat(res.statusCode()).as("Builder query tickets").isEqualTo(200);

        Number totalElements = res.body().path("totalElements");

        // We created exactly 2 leads (2 different phones), so total should be 2
        assertThat(totalElements.longValue())
                .as("BUG: Total tickets should be 2 (one per lead), not more (duplicates from dual managers)")
                .isEqualTo(2);
    }

    @Test
    @Order(510)
    void s3_02_cpSeesExactlyTwoTickets() {
        // The CP themselves should also see exactly 2 tickets, not more
        Response res = cpApi.listTickets(0, 100);
        assertThat(res.statusCode()).as("CP list tickets").isEqualTo(200);

        List<Map<String, Object>> content = res.body().path("content");
        assertThat(content)
                .as("BUG: CP should see exactly 2 tickets (one per lead), not duplicates from dual managers")
                .hasSize(2);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Utility
    // ═══════════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> mapOf(Object... keyValues) {
        Map<Object, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return (Map<K, V>) map;
    }
}
