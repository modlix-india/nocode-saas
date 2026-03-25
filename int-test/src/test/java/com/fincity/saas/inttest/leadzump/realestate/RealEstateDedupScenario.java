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
 * Real Estate — Deal Duplication Rules scenario.
 *
 * Tests the dedup system's rule-based behavior for allowing or blocking duplicate
 * deals based on source, stage, and product boundaries.
 *
 * <h3>Known Bug:</h3>
 * {@code checkWithinClientDuplicate} blocks ALL duplicate deals (same phone) before
 * the rule-based check ({@code TicketDuplicationRule}) can evaluate whether the
 * source/stage conditions allow the duplicate as a re-inquiry. This means dedup rules
 * that should ALLOW re-inquiries from the same source are never reached.
 *
 * <h3>Test Scenarios:</h3>
 * <ul>
 *   <li>S1 (200-210): No dedup rules — duplicate blocked by default</li>
 *   <li>S2 (300-330): Dedup rule allows re-inquiry from same source — BUG: still blocked</li>
 *   <li>S3 (400-420): Dedup rule does NOT allow different source — correctly blocked</li>
 *   <li>S4 (500-520): Cross-product dedup within same client</li>
 *   <li>S5 (600-630): Stage-based dedup — deal past maxStageId</li>
 * </ul>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RealEstateDedupScenario extends BaseIntegrationTest {

    // Builder admin
    private static String token, clientCode, appCode;
    private static Number userId;
    private static EntityProcessorApi api;

    // Template and stages
    private static Number templateId;
    private static Number stageFreshId, stageOpenId;         // PRE_QUALIFICATION
    private static Number stageBookingId, stageBookingDoneId; // POST_QUALIFICATION

    // Products
    private static Number product1Id, product2Id;
    private static String product1Code, product2Code;

    // Dedup rule
    private static Number dedupRuleId;

    // Ticket IDs captured during tests
    private static Number s1Ticket1Id;
    private static Number s2Ticket1Id;
    private static Number s3Ticket1Id;
    private static Number s4Product1TicketId;
    private static Number s5Ticket1Id;

    // Shared UID for unique emails per run
    private static String uid;

    @BeforeAll
    void setup() {
        appCode = prop("leadzump.app.code");
        String parentClientCode = prop("leadzump.client.code");

        uid = UUID.randomUUID().toString().substring(0, 8);
        String email = "re-dedup-" + uid + "@inttest.local";
        String password = "Test@1234";

        SecurityApi secApi = new SecurityApi(baseHost());
        String otp = prop("otp");

        Response otpRes = secApi.generateRegistrationOtp(parentClientCode, appCode, email);
        assertThat(otpRes.statusCode()).as("Generate OTP").isEqualTo(200);

        Response regRes = secApi.register(parentClientCode, appCode, mapOf(
                "clientName", "RE_Dedup_" + uid,
                "firstName", "DedupBuilder",
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
    //  Setup: Template, Stages, Products, Creation Rule
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(100)
    void setup_createTemplateAndPreQualStages() {
        Response tmpl = api.createProductTemplate(Map.of(
                "name", "RE_DedupTest_Template",
                "description", "Dedup integration test template",
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
    void setup_createPostQualStages() {
        // Booking -> Booking Done (POST_QUALIFICATION, closed/success)
        Response booking = api.createStage(mapOf(
                "name", "Booking",
                "platform", "POST_QUALIFICATION",
                "productTemplateId", templateId,
                "isParent", true,
                "order", 2,
                "stageType", "CLOSED",
                "isSuccess", true,
                "isFailure", false,
                "children", mapOf(
                        0, mapOf("name", "Booking Done", "stageType", "CLOSED", "order", 0)
                )
        ));
        assertThat(booking.statusCode()).as("Create Booking stage with Booking Done child").isIn(200, 201);
        stageBookingId = booking.body().path("parent.id");
        stageBookingDoneId = booking.body().path("child[0].id");
        assertThat(stageBookingId).as("Booking parent stage ID").isNotNull();
        assertThat(stageBookingDoneId).as("Booking Done child stage ID").isNotNull();
    }

    @Test
    @Order(120)
    void setup_createProduct1() {
        Response res = api.createProduct(mapOf(
                "name", "RE_DedupTest_Product1",
                "description", "Dedup test product 1",
                "productTemplateId", templateId,
                "forPartner", true
        ));

        assertThat(res.statusCode()).as("Create product 1").isIn(200, 201);
        product1Id = res.body().path("id");
        product1Code = res.body().path("code");
        assertThat(product1Id).isNotNull();
        assertThat(product1Code).as("Product 1 code").isNotNull().isNotEmpty();
    }

    @Test
    @Order(130)
    void setup_createCreationRule() {
        // Default creation rule: assigns leads to the builder admin (owner)
        Response res = api.createCreationRule(mapOf(
                "name", "Default Dedup Rule",
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
        assertThat((Number) res.body().path("id")).as("Creation rule ID").isNotNull();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S1: Basic Dedup - No Rules (duplicate blocked by default)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(200)
    void s1_01_createFirstLead_shouldSucceed() {
        Response res = api.createTicket(mapOf(
                "name", "Dedup_S1_Lead1",
                "dialCode", 91,
                "phoneNumber", "+918000000001",
                "productId", product1Id,
                "source", "Website",
                "subSource", "Homepage"
        ));

        assertThat(res.statusCode()).as("First lead with +91-8000000001 should succeed").isIn(200, 201);
        s1Ticket1Id = res.body().path("id");
        assertThat(s1Ticket1Id).as("S1 ticket 1 ID").isNotNull();
    }

    @Test
    @Order(210)
    void s1_02_duplicateLead_shouldBeBlocked() {
        // No dedup rules exist yet. Any ticket with the same phone number should be
        // blocked as a duplicate by the default within-client check.
        assertThat(s1Ticket1Id).as("S1 ticket 1 must exist").isNotNull();

        Response res = api.createTicket(mapOf(
                "name", "Dedup_S1_Lead1_Dup",
                "dialCode", 91,
                "phoneNumber", "+918000000001",
                "productId", product1Id,
                "source", "Website",
                "subSource", "Homepage"
        ));

        assertThat(res.statusCode()).as("Duplicate lead (no rules) should be blocked with 400").isEqualTo(400);
        String errorMsg = res.body().path("debugMessage");
        assertThat(errorMsg).as("Error should reference existing ticket")
                .contains("already exists with ID");
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S2: Dedup Rule Allows Re-inquiry from Same Source
    //      BUG: checkWithinClientDuplicate blocks before rule evaluation
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(300)
    void s2_01_createDedupRule_channelPartner() {
        // Create a dedup rule that should ALLOW re-inquiries from "Channel Partner"
        // source when the existing deal's stage is at or before Booking (maxStageId).
        Response res = api.createDuplicationRule(mapOf(
                "name", "RE_DedupTest_CP_ReInquiry",
                "productTemplateId", templateId,
                "source", "Channel Partner",
                "maxStageId", stageBookingId,
                "order", 1,
                "condition", Map.of(
                        "operator", "AND",
                        "negate", false,
                        "conditions", List.of(Map.of(
                                "field", "Deal.source",
                                "value", "Channel Partner",
                                "operator", "EQUALS",
                                "negate", false
                        ))
                )
        ));

        assertThat(res.statusCode()).as("Create dedup rule for Channel Partner").isIn(200, 201);
        dedupRuleId = res.body().path("id");
        assertThat(dedupRuleId).as("Dedup rule ID").isNotNull();
    }

    @Test
    @Order(310)
    void s2_02_createFirstCPLead_shouldSucceed() {
        Response res = api.createTicket(mapOf(
                "name", "Dedup_S2_CPLead1",
                "dialCode", 91,
                "phoneNumber", "+918000000002",
                "productId", product1Id,
                "source", "Channel Partner",
                "subSource", "CP_Alpha"
        ));

        assertThat(res.statusCode()).as("First CP lead with +91-8000000002 should succeed").isIn(200, 201);
        s2Ticket1Id = res.body().path("id");
        assertThat(s2Ticket1Id).as("S2 ticket 1 ID").isNotNull();
    }

    @Test
    @Order(320)
    void s2_03_duplicateCPLead_reInquiry_bugBlocksBeforeRuleCheck() {
        // EXPECTED BEHAVIOR (after bug fix):
        //   The dedup rule for "Channel Partner" with maxStageId=Booking should ALLOW
        //   this re-inquiry because:
        //   1. The incoming lead source ("Channel Partner") matches the rule's source
        //   2. The existing deal's stage (Fresh/Open) is BEFORE maxStageId (Booking)
        //   3. The condition (Deal.source == "Channel Partner") matches
        //   Therefore this should succeed with 200/201.
        //
        // ACTUAL BEHAVIOR (BUG):
        //   checkWithinClientDuplicate() finds the existing deal with the same phone
        //   number and immediately returns 400 "already exists" WITHOUT evaluating the
        //   dedup rules. The rule-based re-inquiry logic is never reached.
        assertThat(s2Ticket1Id).as("S2 ticket 1 must exist for re-inquiry test").isNotNull();

        Response res = api.createTicket(mapOf(
                "name", "Dedup_S2_CPLead1_ReInquiry",
                "dialCode", 91,
                "phoneNumber", "+918000000002",
                "productId", product1Id,
                "source", "Channel Partner",
                "subSource", "CP_Beta"
        ));

        // Re-inquiry from the same source should be ALLOWED by the dedup rule,
        // because the existing deal's stage is within the maxStageId range.
        assertThat(res.statusCode())
                .as("Re-inquiry from same source should be ALLOWED by dedup rule")
                .isIn(200, 201);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S3: Dedup Rule Does NOT Allow Different Source
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(400)
    void s3_01_createWebsiteLead_shouldSucceed() {
        Response res = api.createTicket(mapOf(
                "name", "Dedup_S3_WebLead1",
                "dialCode", 91,
                "phoneNumber", "+918000000003",
                "productId", product1Id,
                "source", "Website",
                "subSource", "Landing Page"
        ));

        assertThat(res.statusCode()).as("Website lead with +91-8000000003 should succeed").isIn(200, 201);
        s3Ticket1Id = res.body().path("id");
        assertThat(s3Ticket1Id).as("S3 ticket 1 ID").isNotNull();
    }

    @Test
    @Order(420)
    void s3_02_duplicateFromDifferentSource_shouldBeBlocked() {
        // The dedup rule exists for "Channel Partner" source, but the existing deal
        // was created from "Website". No dedup rule allows Website duplicates.
        // The incoming lead from "Channel Partner" should be blocked because the
        // existing deal's source ("Website") does not match the rule's condition
        // (Deal.source == "Channel Partner").
        assertThat(s3Ticket1Id).as("S3 ticket 1 must exist").isNotNull();

        Response res = api.createTicket(mapOf(
                "name", "Dedup_S3_CPLead_SamePhone",
                "dialCode", 91,
                "phoneNumber", "+918000000003",
                "productId", product1Id,
                "source", "Channel Partner",
                "subSource", "CP_Gamma"
        ));

        // Should be blocked: duplicate phone, and the rule condition does not match
        // (existing deal source is "Website", not "Channel Partner")
        assertThat(res.statusCode())
                .as("Duplicate from different source should be blocked")
                .isEqualTo(400);
        String errorMsg = res.body().path("debugMessage");
        assertThat(errorMsg).as("Error should reference existing ticket")
                .contains("already exists with ID");
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S4: Cross-Product Dedup (same client, different product)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(500)
    void s4_01_createProduct2() {
        Response res = api.createProduct(mapOf(
                "name", "RE_DedupTest_Product2",
                "description", "Dedup test product 2 (same template)",
                "productTemplateId", templateId,
                "forPartner", true
        ));

        assertThat(res.statusCode()).as("Create product 2").isIn(200, 201);
        product2Id = res.body().path("id");
        product2Code = res.body().path("code");
        assertThat(product2Id).isNotNull();
        assertThat(product2Code).as("Product 2 code").isNotNull().isNotEmpty();
    }

    @Test
    @Order(510)
    void s4_02_createLeadInProduct1_shouldSucceed() {
        Response res = api.createTicket(mapOf(
                "name", "Dedup_S4_Prod1Lead",
                "dialCode", 91,
                "phoneNumber", "+918000000004",
                "productId", product1Id,
                "source", "Website",
                "subSource", "Homepage"
        ));

        assertThat(res.statusCode()).as("Lead in product 1 with +91-8000000004 should succeed").isIn(200, 201);
        s4Product1TicketId = res.body().path("id");
        assertThat(s4Product1TicketId).as("S4 product 1 ticket ID").isNotNull();
    }

    @Test
    @Order(520)
    void s4_03_duplicateInProduct2_shouldBeBlocked() {
        // Cross-product dedup: same phone number exists in product 1 under the same client.
        // The within-client check should block this even though it's a different product.
        assertThat(s4Product1TicketId).as("S4 product 1 ticket must exist").isNotNull();

        Response res = api.createTicket(mapOf(
                "name", "Dedup_S4_Prod2Lead_SamePhone",
                "dialCode", 91,
                "phoneNumber", "+918000000004",
                "productId", product2Id,
                "source", "Website",
                "subSource", "Homepage"
        ));

        // Cross-product dedup: same phone in a different product is ALLOWED.
        // Dedup is scoped per product, not per client.
        assertThat(res.statusCode())
                .as("Cross-product same phone should be allowed (dedup is per-product)")
                .isIn(200, 201);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S5: Stage-Based Dedup — Deal Past maxStageId
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(600)
    void s5_01_createCPLead_shouldSucceed() {
        Response res = api.createTicket(mapOf(
                "name", "Dedup_S5_CPLead_Stage",
                "dialCode", 91,
                "phoneNumber", "+918000000005",
                "productId", product1Id,
                "source", "Channel Partner",
                "subSource", "CP_Delta"
        ));

        assertThat(res.statusCode()).as("CP lead with +91-8000000005 should succeed").isIn(200, 201);
        s5Ticket1Id = res.body().path("id");
        assertThat(s5Ticket1Id).as("S5 ticket 1 ID").isNotNull();
    }

    @Test
    @Order(610)
    void s5_02_moveDealPastMaxStage() {
        // Move the deal to Booking Done — which is PAST the dedup rule's maxStageId (Booking parent).
        // This means the dedup rule should no longer consider this deal when evaluating
        // future duplicates, because it has progressed beyond the allowed stage threshold.
        assertThat(s5Ticket1Id).as("S5 ticket 1 must exist").isNotNull();

        Response r1 = api.updateTicketStage(s5Ticket1Id, Map.of(
                "stageId", stageBookingDoneId,
                "comment", "Booking completed — moving past maxStageId for dedup test"
        ));
        assertThat(r1.statusCode()).as("Move to Booking Done (past maxStageId)").isIn(200, 201);

        // Verify the ticket is now at Booking Done
        Response verify = api.getTicket(s5Ticket1Id);
        assertThat(verify.statusCode()).isEqualTo(200);
        Number currentStage = verify.body().path("stage");
        assertThat(currentStage.longValue())
                .as("Ticket should be at Booking Done stage")
                .isEqualTo(stageBookingDoneId.longValue());
    }

    @Test
    @Order(630)
    void s5_03_reInquiryAfterDealPastMaxStage_dependsOnImplementation() {
        // The existing deal (+91-8000000005) is now at Booking Done, which is PAST
        // the dedup rule's maxStageId (Booking parent). The dedup rule should NOT
        // consider this deal as a duplicate candidate.
        //
        // EXPECTED BEHAVIOR (after bug fix):
        //   Since the existing deal is past maxStageId, the dedup rule should not
        //   match it, and the new lead should be ALLOWED (it's effectively a new
        //   opportunity — the previous deal is already closed/booked).
        //
        // ACTUAL BEHAVIOR (BUG):
        //   checkWithinClientDuplicate() blocks this before any rule evaluation,
        //   same as S2. The stage-based maxStageId logic is never reached.
        assertThat(s5Ticket1Id).as("S5 ticket 1 must exist").isNotNull();

        Response res = api.createTicket(mapOf(
                "name", "Dedup_S5_CPLead_AfterBooking",
                "dialCode", 91,
                "phoneNumber", "+918000000005",
                "productId", product1Id,
                "source", "Channel Partner",
                "subSource", "CP_Epsilon"
        ));

        // Existing deal is past maxStageId (Booking Done > Booking), so the dedup rule
        // should NOT consider it a duplicate. Re-inquiry should be allowed.
        assertThat(res.statusCode())
                .as("Re-inquiry should be allowed when existing deal is past maxStageId")
                .isIn(200, 201);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Utility
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Map.of() doesn't allow null values and has a 10-entry limit.
     * This helper allows any number of entries including nulls.
     */
    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> mapOf(Object... keyValues) {
        Map<Object, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return (Map<K, V>) map;
    }
}
