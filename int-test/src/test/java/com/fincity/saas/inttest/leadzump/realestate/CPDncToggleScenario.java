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
 * Channel Partner DNC Toggle Scenario.
 *
 * Verifies that when a channel partner toggles their DNC (Do Not Call) flag:
 *   1. The partner's DNC flag is correctly toggled
 *   2. All tickets belonging to the partner have their DNC flag synced
 *   3. The expiresOn field on tickets is NOT recalculated (the bug being fixed)
 *
 * Setup:
 *   - Builder admin registers, creates template/stages/product/expiry rule
 *   - Two team members invited for round-robin
 *   - A channel partner registers, partner entry created, CP authenticates
 *   - CP creates two tickets (with Walk-in source so expiresOn is set via the 30-day rule)
 *
 * Test Scenarios:
 *   S1 (500-520): CP toggles DNC ON — partner.dnc=true, tickets.dnc=true, expiresOn unchanged
 *   S2 (600-620): CP toggles DNC OFF — partner.dnc=false, tickets.dnc=false, expiresOn unchanged
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CPDncToggleScenario extends BaseIntegrationTest {

    // Builder admin
    private static String token, clientCode, appCode, parentClientCode;
    private static Number userId;
    private static EntityProcessorApi api;

    // Template, product, stages
    private static Number templateId;
    private static Number productId;

    // Team members
    private static Number member1UserId, member2UserId;
    private static Number salesMemberProfileId;

    // Channel partner
    private static String cpToken, cpEmail;
    private static Number cpClientId;
    private static EntityProcessorApi cpApi;

    // Tickets created by CP
    private static Number ticket1Id, ticket2Id;

    // ExpiresOn values captured before DNC toggle
    private static Number ticket1ExpiresOnBefore, ticket2ExpiresOnBefore;

    // UpdatedAt values captured before DNC toggle
    private static Number ticket1UpdatedAtBefore, ticket2UpdatedAtBefore;

    private static String uid;
    private static SecurityApi secApi;

    @BeforeAll
    void setup() {
        appCode = prop("leadzump.app.code");
        parentClientCode = prop("leadzump.client.code");

        uid = UUID.randomUUID().toString().substring(0, 8);
        String email = "re-cpdnc-" + uid + "@inttest.local";
        String password = "Test@1234";

        secApi = new SecurityApi(baseHost());

        Response otpRes = secApi.generateRegistrationOtp(parentClientCode, appCode, email);
        assertThat(otpRes.statusCode()).as("Generate OTP").isEqualTo(200);

        Response regRes = secApi.register(parentClientCode, appCode, mapOf(
                "clientName", "RE_CPDnc_" + uid,
                "firstName", "CPDncBuilder",
                "lastName", "IntTest",
                "emailId", email,
                "password", password,
                "passType", "PASSWORD",
                "businessClient", true,
                "otp", prop("otp")
        ));
        assertThat(regRes.statusCode()).as("Self-registration").isIn(200, 201);

        clientCode = regRes.body().path("authentication.client.code");
        userId = regRes.body().path("authentication.user.id");

        Response authRes = secApi.authenticate(parentClientCode, appCode, email, password);
        assertThat(authRes.statusCode()).as("Post-registration auth").isEqualTo(200);
        token = authRes.body().path("accessToken");
        assertThat(token).as("Auth token").isNotNull().isNotEmpty();

        if (clientCode == null || clientCode.isBlank()) {
            clientCode = authRes.body().path("user.clientCode");
        }

        api = new EntityProcessorApi(givenAuth(token, parentClientCode, appCode));

        ProfileHelper profiles = ProfileHelper.load(secApi, token, parentClientCode, appCode);
        salesMemberProfileId = profiles.getByName("Sales Member");
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Setup: Template, Stages, Product, Expiration Rule
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(100)
    void setup_createTemplateAndStages() {
        Response tmpl = api.createProductTemplate(Map.of(
                "name", "RE_CPDncTest_Template",
                "description", "CP DNC toggle test",
                "productTemplateType", "GENERAL"
        ));
        assertThat(tmpl.statusCode()).as("Create product template").isIn(200, 201);
        templateId = tmpl.body().path("id");

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
        assertThat(fresh.statusCode()).as("Create Fresh stage").isIn(200, 201);
    }

    @Test
    @Order(110)
    void setup_createProduct() {
        Response res = api.createProduct(mapOf(
                "name", "RE_CPDncTest_Product",
                "description", "CP DNC toggle test product",
                "productTemplateId", templateId,
                "forPartner", true
        ));
        assertThat(res.statusCode()).as("Create product").isIn(200, 201);
        productId = res.body().path("id");
        assertThat(productId).isNotNull();
    }

    @Test
    @Order(120)
    void setup_createExpirationRule() {
        Response res = api.createExpirationRule(mapOf(
                "name", "CPDnc_WalkIn_Expiry_30d",
                "productId", productId,
                "source", "Walk-in",
                "expiryDays", 30
        ));
        assertThat(res.statusCode()).as("Create expiration rule").isIn(200, 201);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Setup: Team members for round-robin
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(200)
    void setup_inviteMember1() {
        Response inv = secApi.inviteUser(token, parentClientCode, appCode, mapOf(
                "emailId", "cpdnc-m1-" + uid + "@inttest.local",
                "firstName", "Member1", "lastName", "IntTest",
                "profileId", salesMemberProfileId, "reportingTo", userId
        ));
        assertThat(inv.statusCode()).as("Invite Member1").isIn(200, 201);
        String code = inv.body().path("userRequest.inviteCode");

        Response acc = secApi.acceptInvite(parentClientCode, appCode, mapOf(
                "emailId", "cpdnc-m1-" + uid + "@inttest.local",
                "firstName", "Member1", "lastName", "IntTest",
                "password", "Test@1234", "passType", "PASSWORD", "inviteCode", code
        ));
        assertThat(acc.statusCode()).as("Accept Member1").isIn(200, 201);
        member1UserId = acc.body().path("authentication.user.id");
    }

    @Test
    @Order(210)
    void setup_inviteMember2() {
        Response inv = secApi.inviteUser(token, parentClientCode, appCode, mapOf(
                "emailId", "cpdnc-m2-" + uid + "@inttest.local",
                "firstName", "Member2", "lastName", "IntTest",
                "profileId", salesMemberProfileId, "reportingTo", userId
        ));
        assertThat(inv.statusCode()).as("Invite Member2").isIn(200, 201);
        String code = inv.body().path("userRequest.inviteCode");

        Response acc = secApi.acceptInvite(parentClientCode, appCode, mapOf(
                "emailId", "cpdnc-m2-" + uid + "@inttest.local",
                "firstName", "Member2", "lastName", "IntTest",
                "password", "Test@1234", "passType", "PASSWORD", "inviteCode", code
        ));
        assertThat(acc.statusCode()).as("Accept Member2").isIn(200, 201);
        member2UserId = acc.body().path("authentication.user.id");
    }

    @Test
    @Order(220)
    void setup_createCreationRule() {
        Response res = api.createCreationRule(mapOf(
                "name", "CPDnc RR Rule",
                "productTemplateId", templateId,
                "order", 0,
                "userDistributionType", "ROUND_ROBIN",
                "userDistributions", List.of(
                        Map.of("userId", member1UserId),
                        Map.of("userId", member2UserId)
                )
        ));
        assertThat(res.statusCode()).as("Create creation rule").isIn(200, 201);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Setup: Register CP, Create Partner, Authenticate CP
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(300)
    void setup_registerChannelPartner() {
        cpEmail = "cpdnc-cp-" + uid + "@inttest.local";

        Response regRes = secApi.registerClient(token, parentClientCode, appCode, mapOf(
                "clientName", "CPDnc_CP_" + uid,
                "firstName", "CPAdmin", "lastName", "IntTest",
                "emailId", cpEmail, "password", "Test@1234",
                "passType", "PASSWORD",
                "businessClient", true
        ));
        assertThat(regRes.statusCode()).as("Register CP").isIn(200, 201);

        cpClientId = regRes.body().path("authentication.client.id");
        if (cpClientId == null) {
            cpClientId = regRes.body().path("client.id");
        }
        assertThat(cpClientId).as("CP client ID").isNotNull();
    }

    @Test
    @Order(310)
    void setup_createPartner() {
        Response res = api.createPartner(mapOf(
                "name", "CPDnc_Partner_" + uid,
                "clientId", cpClientId
        ));
        assertThat(res.statusCode()).as("Create partner for CP").isIn(200, 201);
    }

    @Test
    @Order(320)
    void setup_authenticateCP() {
        Response cpAuthRes = secApi.authenticate(clientCode, appCode, cpEmail, "Test@1234");
        assertThat(cpAuthRes.statusCode()).as("CP auth").isEqualTo(200);
        cpToken = cpAuthRes.body().path("accessToken");
        assertThat(cpToken).as("CP token").isNotNull().isNotEmpty();

        cpApi = new EntityProcessorApi(givenAuth(cpToken, clientCode, appCode));
    }

    @Test
    @Order(330)
    void setup_assignClientManager() {
        Response r1 = secApi.assignClientManager(token, parentClientCode, appCode, member1UserId, cpClientId);
        assertThat(r1.statusCode()).as("Assign client manager to CP").isIn(200, 201);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Setup: CP creates two tickets with Walk-in source (triggers expiresOn)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(400)
    void setup_cpCreatesTicket1() {
        Response res = cpApi.createTicket(mapOf(
                "name", "DNC Lead 1",
                "dialCode", 91,
                "phoneNumber", "+919300000001",
                "productId", productId,
                "source", "Walk-in",
                "subSource", "CPDncTest"
        ));
        assertThat(res.statusCode()).as("CP create ticket 1").isIn(200, 201);
        ticket1Id = res.body().path("id");
        assertThat(ticket1Id).as("Ticket 1 ID").isNotNull();
    }

    @Test
    @Order(410)
    void setup_cpCreatesTicket2() {
        Response res = cpApi.createTicket(mapOf(
                "name", "DNC Lead 2",
                "dialCode", 91,
                "phoneNumber", "+919300000002",
                "productId", productId,
                "source", "Walk-in",
                "subSource", "CPDncTest"
        ));
        assertThat(res.statusCode()).as("CP create ticket 2").isIn(200, 201);
        ticket2Id = res.body().path("id");
        assertThat(ticket2Id).as("Ticket 2 ID").isNotNull();
    }

    @Test
    @Order(420)
    void setup_verifyTicketsHaveExpiresOn() {
        Response t1 = api.getTicket(ticket1Id);
        assertThat(t1.statusCode()).isEqualTo(200);
        assertThat((Number) t1.body().path("expiresOn"))
                .as("Ticket 1 should have expiresOn set (Walk-in + 30d rule)")
                .isNotNull();

        Response t2 = api.getTicket(ticket2Id);
        assertThat(t2.statusCode()).isEqualTo(200);
        assertThat((Number) t2.body().path("expiresOn"))
                .as("Ticket 2 should have expiresOn set")
                .isNotNull();

        Boolean t1Dnc = t1.body().path("dnc");
        Boolean t2Dnc = t2.body().path("dnc");
        assertThat(t1Dnc).as("Ticket 1 DNC should default to false").isFalse();
        assertThat(t2Dnc).as("Ticket 2 DNC should default to false").isFalse();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S1: CP toggles DNC ON — tickets.dnc=true, expiresOn unchanged
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(500)
    void s1_captureExpiresOnBeforeToggle() {
        Response t1 = api.getTicket(ticket1Id);
        assertThat(t1.statusCode()).isEqualTo(200);
        ticket1ExpiresOnBefore = t1.body().path("expiresOn");
        ticket1UpdatedAtBefore = t1.body().path("updatedAt");

        Response t2 = api.getTicket(ticket2Id);
        assertThat(t2.statusCode()).isEqualTo(200);
        ticket2ExpiresOnBefore = t2.body().path("expiresOn");
        ticket2UpdatedAtBefore = t2.body().path("updatedAt");

        assertThat(ticket1ExpiresOnBefore).as("Ticket 1 expiresOn before toggle").isNotNull();
        assertThat(ticket2ExpiresOnBefore).as("Ticket 2 expiresOn before toggle").isNotNull();
        assertThat(ticket1UpdatedAtBefore).as("Ticket 1 updatedAt before toggle").isNotNull();
        assertThat(ticket2UpdatedAtBefore).as("Ticket 2 updatedAt before toggle").isNotNull();
    }

    @Test
    @Order(510)
    void s1_cpTogglesDncOn() throws InterruptedException {
        // Small delay so any expiresOn recalculation would be detectably different
        Thread.sleep(2000);

        Response res = cpApi.toggleMyDnc();
        assertThat(res.statusCode())
                .as("CP toggle DNC ON: " + res.body().asString())
                .isIn(200, 201);

        Boolean dnc = res.body().path("dnc");
        assertThat(dnc).as("Partner DNC should now be true").isTrue();
    }

    @Test
    @Order(520)
    void s1_verifyTicketsDncOnAndTimestampsUnchanged() {
        Response t1 = api.getTicket(ticket1Id);
        assertThat(t1.statusCode()).isEqualTo(200);
        Boolean t1Dnc = t1.body().path("dnc");
        Number t1ExpiresOn = t1.body().path("expiresOn");
        Number t1UpdatedAt = t1.body().path("updatedAt");

        assertThat(t1Dnc).as("Ticket 1 DNC should be true after partner DNC toggle ON").isTrue();
        assertThat(t1ExpiresOn).as("Ticket 1 expiresOn should not be null").isNotNull();
        assertThat(t1ExpiresOn.longValue())
                .as("Ticket 1 expiresOn should NOT change when DNC is toggled — before: %d, after: %d",
                        ticket1ExpiresOnBefore.longValue(), t1ExpiresOn.longValue())
                .isEqualTo(ticket1ExpiresOnBefore.longValue());
        assertThat(t1UpdatedAt.longValue())
                .as("Ticket 1 updatedAt should NOT change when DNC is toggled — before: %d, after: %d",
                        ticket1UpdatedAtBefore.longValue(), t1UpdatedAt.longValue())
                .isEqualTo(ticket1UpdatedAtBefore.longValue());

        Response t2 = api.getTicket(ticket2Id);
        assertThat(t2.statusCode()).isEqualTo(200);
        Boolean t2Dnc = t2.body().path("dnc");
        Number t2ExpiresOn = t2.body().path("expiresOn");
        Number t2UpdatedAt = t2.body().path("updatedAt");

        assertThat(t2Dnc).as("Ticket 2 DNC should be true after partner DNC toggle ON").isTrue();
        assertThat(t2ExpiresOn).as("Ticket 2 expiresOn should not be null").isNotNull();
        assertThat(t2ExpiresOn.longValue())
                .as("Ticket 2 expiresOn should NOT change when DNC is toggled — before: %d, after: %d",
                        ticket2ExpiresOnBefore.longValue(), t2ExpiresOn.longValue())
                .isEqualTo(ticket2ExpiresOnBefore.longValue());
        assertThat(t2UpdatedAt.longValue())
                .as("Ticket 2 updatedAt should NOT change when DNC is toggled — before: %d, after: %d",
                        ticket2UpdatedAtBefore.longValue(), t2UpdatedAt.longValue())
                .isEqualTo(ticket2UpdatedAtBefore.longValue());
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S2: CP toggles DNC OFF — tickets.dnc=false, expiresOn unchanged
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(600)
    void s2_captureExpiresOnBeforeToggleOff() {
        Response t1 = api.getTicket(ticket1Id);
        assertThat(t1.statusCode()).isEqualTo(200);
        ticket1ExpiresOnBefore = t1.body().path("expiresOn");
        ticket1UpdatedAtBefore = t1.body().path("updatedAt");

        Response t2 = api.getTicket(ticket2Id);
        assertThat(t2.statusCode()).isEqualTo(200);
        ticket2ExpiresOnBefore = t2.body().path("expiresOn");
        ticket2UpdatedAtBefore = t2.body().path("updatedAt");
    }

    @Test
    @Order(610)
    void s2_cpTogglesDncOff() throws InterruptedException {
        Thread.sleep(2000);

        Response res = cpApi.toggleMyDnc();
        assertThat(res.statusCode())
                .as("CP toggle DNC OFF: " + res.body().asString())
                .isIn(200, 201);

        Boolean dnc = res.body().path("dnc");
        assertThat(dnc).as("Partner DNC should now be false").isFalse();
    }

    @Test
    @Order(620)
    void s2_verifyTicketsDncOffAndTimestampsUnchanged() {
        Response t1 = api.getTicket(ticket1Id);
        assertThat(t1.statusCode()).isEqualTo(200);
        Boolean t1Dnc = t1.body().path("dnc");
        Number t1ExpiresOn = t1.body().path("expiresOn");
        Number t1UpdatedAt = t1.body().path("updatedAt");

        assertThat(t1Dnc).as("Ticket 1 DNC should be false after partner DNC toggle OFF").isFalse();
        assertThat(t1ExpiresOn.longValue())
                .as("Ticket 1 expiresOn should NOT change on DNC toggle OFF — before: %d, after: %d",
                        ticket1ExpiresOnBefore.longValue(), t1ExpiresOn.longValue())
                .isEqualTo(ticket1ExpiresOnBefore.longValue());
        assertThat(t1UpdatedAt.longValue())
                .as("Ticket 1 updatedAt should NOT change on DNC toggle OFF — before: %d, after: %d",
                        ticket1UpdatedAtBefore.longValue(), t1UpdatedAt.longValue())
                .isEqualTo(ticket1UpdatedAtBefore.longValue());

        Response t2 = api.getTicket(ticket2Id);
        assertThat(t2.statusCode()).isEqualTo(200);
        Boolean t2Dnc = t2.body().path("dnc");
        Number t2ExpiresOn = t2.body().path("expiresOn");
        Number t2UpdatedAt = t2.body().path("updatedAt");

        assertThat(t2Dnc).as("Ticket 2 DNC should be false after partner DNC toggle OFF").isFalse();
        assertThat(t2ExpiresOn.longValue())
                .as("Ticket 2 expiresOn should NOT change on DNC toggle OFF — before: %d, after: %d",
                        ticket2ExpiresOnBefore.longValue(), t2ExpiresOn.longValue())
                .isEqualTo(ticket2ExpiresOnBefore.longValue());
        assertThat(t2UpdatedAt.longValue())
                .as("Ticket 2 updatedAt should NOT change on DNC toggle OFF — before: %d, after: %d",
                        ticket2UpdatedAtBefore.longValue(), t2UpdatedAt.longValue())
                .isEqualTo(ticket2UpdatedAtBefore.longValue());
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S3: Verify partner state via getMyPartner
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(700)
    void s3_verifyPartnerDncViaGetMyPartner() {
        Response res = cpApi.getMyPartner();
        assertThat(res.statusCode()).as("Get my partner").isEqualTo(200);

        Boolean dnc = res.body().path("dnc");
        assertThat(dnc).as("Partner DNC should be false after two toggles (ON then OFF)").isFalse();
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
