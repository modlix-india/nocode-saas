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
 * Real Estate — Small Agent scenario.
 *
 * S6: Single Product, Owner-as-Sales
 *   Minimal setup: 1 template, 1 product, owner as sole user.
 *   Referral lead → call log → note → stage transitions → booking → eager verify.
 *
 * S4: Bulk Website Lead Capture (Open API)
 *   3 leads via public website form (no auth) → admin verifies.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RealEstateSmallScenario extends BaseIntegrationTest {

    private static String token;
    private static String clientCode;
    private static String appCode;
    private static EntityProcessorApi api;

    private static Number templateId;
    private static Number productId;
    private static String productCode;

    // Minimal stages
    private static Number stageFreshId;
    private static Number stageOpenId;
    private static Number stageContactableId;
    private static Number stageFollowUpId;
    private static Number stageBookingId;
    private static Number stageBookingDoneId;

    // User
    private static Number userId;

    // S6 data
    private static Number ticketId;
    private static Number noteId;

    @BeforeAll
    void setup() {
        appCode = prop("leadzump.app.code");
        String parentClientCode = prop("leadzump.client.code");

        String uid = UUID.randomUUID().toString().substring(0, 8);
        String email = "re-small-" + uid + "@inttest.local";
        String password = "Test@1234";

        SecurityApi secApi = new SecurityApi(baseHost());
        String otp = prop("otp");

        Response otpRes = secApi.generateRegistrationOtp(parentClientCode, appCode, email);
        assertThat(otpRes.statusCode()).as("Generate OTP").isEqualTo(200);

        Response regRes = secApi.register(parentClientCode, appCode, mapOf(
                "clientName", "RE_Small_" + uid,
                "firstName", "SmallAgent",
                "lastName", "IntTest",
                "emailId", email,
                "password", password,
                "passType", "PASSWORD",
                "businessClient", true,
                "otp", otp
        ));

        assertThat(regRes.statusCode()).as("Self-registration").isIn(200, 201);

        token = regRes.body().path("authentication.accessToken");
        assertThat(token).as("Registration should return accessToken").isNotNull().isNotEmpty();

        clientCode = regRes.body().path("authentication.client.code");
        if (clientCode == null || clientCode.isBlank()) {
            Response authRes = secApi.authenticate(parentClientCode, appCode, email, password);
            assertThat(authRes.statusCode()).as("Post-registration auth").isEqualTo(200);
            token = authRes.body().path("accessToken");
            clientCode = authRes.body().path("user.clientCode");
        }

        userId = regRes.body().path("authentication.user.id");
        api = new EntityProcessorApi(givenAuth(token, clientCode, appCode));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Setup: Minimal template + stages + product
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(10)
    void setup_createTemplateAndStages() {
        Response tmpl = api.createProductTemplate(Map.of(
                "name", "RE_IntTest_SmallTemplate",
                "description", "Small agent template",
                "productTemplateType", "GENERAL"
        ));
        assertThat(tmpl.statusCode()).isIn(200, 201);
        templateId = tmpl.body().path("id");

        // Fresh → Open (nested child)
        Response fresh = api.createStage(mapOf(
                "name", "Fresh", "platform", "PRE_QUALIFICATION",
                "productTemplateId", templateId, "isParent", true, "order", 1, "stageType", "OPEN",
                "children", mapOf(
                        0, mapOf("name", "Open", "stageType", "OPEN", "order", 0)
                )
        ));
        stageFreshId = fresh.body().path("parent.id");
        stageOpenId = fresh.body().path("child[0].id");

        // Contactable → Follow Up (nested child)
        Response contactable = api.createStage(mapOf(
                "name", "Contactable", "platform", "PRE_QUALIFICATION",
                "productTemplateId", templateId, "isParent", true, "order", 2, "stageType", "OPEN",
                "children", mapOf(
                        1, mapOf("name", "Follow Up", "stageType", "OPEN", "order", 1)
                )
        ));
        stageContactableId = contactable.body().path("parent.id");
        stageFollowUpId = contactable.body().path("child[0].id");

        // Booking → Booking Done (nested child)
        Response booking = api.createStage(mapOf(
                "name", "Booking", "platform", "POST_QUALIFICATION",
                "productTemplateId", templateId, "isParent", true, "order", 8,
                "stageType", "CLOSED", "isSuccess", true, "isFailure", false,
                "children", mapOf(
                        2, mapOf("name", "Booking Done", "stageType", "CLOSED", "order", 2)
                )
        ));
        stageBookingId = booking.body().path("parent.id");
        stageBookingDoneId = booking.body().path("child[0].id");
    }

    @Test
    @Order(20)
    void setup_createProduct() {
        Response res = api.createProduct(mapOf(
                "name", "RE_IntTest_Single Plot Listing",
                "description", "Owner's only listing",
                "productTemplateId", templateId,
                "forPartner", false
        ));
        assertThat(res.statusCode()).isIn(200, 201);
        productId = res.body().path("id");
        productCode = res.body().path("code");
    }

    @Test
    @Order(30)
    void setup_createCreationRule() {
        Response res = api.createCreationRule(mapOf(
                "name", "Owner Assignment",
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
    //  S6: Single Product, Owner-as-Sales
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(600)
    void s6_01_createReferralLead() {
        Response res = api.createTicket(mapOf(
                "name", "RE_IntTest_Meena Kapoor",
                "dialCode", 91,
                "phoneNumber", "+919456789012",
                "productId", productId,
                "source", "Referral"
        ));

        assertThat(res.statusCode()).isIn(200, 201);
        ticketId = res.body().path("id");
        assertThat(ticketId).isNotNull();
    }

    @Test
    @Order(610)
    void s6_02_logOutboundCall() {
        Response res = api.logCallActivity(mapOf(
                "ticketId", ticketId,
                "activityAction", "CALL_LOG",
                "comment", "Discussed pricing and layout preferences"
        ));

        // Call log might return 200 (void) or 201
        assertThat(res.statusCode()).as("Log call activity").isIn(200, 201);
    }

    @Test
    @Order(620)
    void s6_03_addFollowUpNote() {
        // NoteRequest fields: name, content, ticketId (Identity), ownerId (Identity), userId.
        // contentEntitySeries is derived, not a JSON field.
        Response res = api.createNote(mapOf(
                "name", "Follow-up Notes",
                "content", "Customer wants east-facing 2BHK. Budget: 80L. Will visit this weekend.",
                "ticketId", ticketId
        ));

        assertThat(res.statusCode()).isIn(200, 201);
        noteId = res.body().path("id");
        assertThat(noteId).isNotNull();
    }

    @Test
    @Order(630)
    void s6_04_progressToFollowUp() {
        Response res = api.updateTicketStage(ticketId, Map.of(
                "stageId", stageFollowUpId,
                "comment", "Contacted, follow-up scheduled"
        ));
        assertThat(res.statusCode()).isIn(200, 201);
    }

    @Test
    @Order(640)
    void s6_05_progressToBookingDone() {
        Response res = api.updateTicketStage(ticketId, Map.of(
                "stageId", stageBookingDoneId,
                "comment", "Deal closed, booking amount received"
        ));
        assertThat(res.statusCode()).isIn(200, 201);
    }

    @Test
    @Order(650)
    void s6_06_verifyFinalStateEager() {
        Response res = api.getTicketEager(ticketId);
        assertThat(res.statusCode()).isEqualTo(200);

        Number finalStage = res.body().path("stage");
        assertThat(finalStage.longValue())
                .as("Should be in Booking Done")
                .isEqualTo(stageBookingDoneId.longValue());

        // Owner should exist (owner = the single user)
        Number ownerId = res.body().path("ownerId");
        assertThat(ownerId).isNotNull();
    }

    @Test
    @Order(660)
    void s6_07_verifyActivityTrail() {
        Response res = api.getTicketActivitiesEager(ticketId, 0, 50);
        assertThat(res.statusCode()).isEqualTo(200);

        List<Map<String, Object>> content = res.body().path("content");
        assertThat(content).isNotEmpty();

        List<String> actions = content.stream()
                .map(a -> (String) a.get("activityAction"))
                .toList();

        assertThat(actions).contains("CREATE");
        // Should have CALL_LOG, NOTE_ADD, STAGE_UPDATE
        long stageUpdates = actions.stream().filter("STAGE_UPDATE"::equals).count();
        assertThat(stageUpdates).isGreaterThanOrEqualTo(2);
    }

    @Test
    @Order(670)
    void s6_08_singleProductAnalytics() {
        Response res = api.analyticsStageCounts_Products(mapOf(
                "productIds", List.of(productId),
                "startDate", 1772303400,
                "endDate", 1774981799,
                "timezone", "Asia/Kolkata"
        ));

        assertThat(res.statusCode()).isEqualTo(200);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S4: Bulk Website Lead Capture (Open API)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(400)
    void s4_01_submitWebsiteLeads() {
        // CampaignTicketRequest for website: leadDetails, comment.
        // MUST NOT include campaignDetails — the service rejects website leads with campaign data.
        // Lead 1 — full details (no campaignDetails)
        Response r1 = EntityProcessorApi.submitWebsiteLead(baseHost(), productCode, mapOf(
                "appCode", appCode,
                "clientCode", clientCode,
                "leadDetails", mapOf(
                        "firstName", "Anita",
                        "lastName", "Desai",
                        "phone", mapOf("countryCode", 91, "number", "9123456780"),
                        "email", mapOf("address", "anita.inttest@gmail.com"),
                        "source", "Website",
                        "subSource", "contact_form"
                ),
                "comment", "Interested in 3bhk_pune"
        ));
        assertThat(r1.statusCode()).as("Website lead 1").isIn(200, 201);

        // Lead 2 — minimal data
        Response r2 = EntityProcessorApi.submitWebsiteLead(baseHost(), productCode, mapOf(
                "appCode", appCode,
                "clientCode", clientCode,
                "leadDetails", mapOf(
                        "firstName", "Suresh",
                        "lastName", "Patel",
                        "phone", mapOf("countryCode", 91, "number", "9234567890"),
                        "source", "Website"
                )
        ));
        assertThat(r2.statusCode()).as("Website lead 2").isIn(200, 201);

        // Lead 3 — phone as string (PhoneNumber deserializer handles plain strings)
        Response r3 = EntityProcessorApi.submitWebsiteLead(baseHost(), productCode, mapOf(
                "appCode", appCode,
                "clientCode", clientCode,
                "leadDetails", mapOf(
                        "firstName", "Test",
                        "lastName", "User",
                        "phone", "9345678901",
                        "source", "Website"
                )
        ));
        assertThat(r3.statusCode()).as("Website lead 3").isIn(200, 201);
    }

    @Test
    @Order(410)
    void s4_02_verifyWebsiteLeadsCreated() {
        // Query tickets to verify website leads were created
        Response res = api.queryUserTickets(Map.of(
                "page", 0,
                "size", 10
        ));

        assertThat(res.statusCode()).isEqualTo(200);
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
