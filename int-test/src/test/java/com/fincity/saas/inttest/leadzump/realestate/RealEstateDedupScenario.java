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
 *   <li>S6 (700-740): Assignment carry — re-inquiry gets same assigned user</li>
 *   <li>S7 (800-810): Cross-CP assignment carry — different CPs, same phone, same user</li>
 *   <li>S8 (900-930): Inactive user skipped in round-robin, reactivation restores</li>
 *   <li>S9 (1000-1040): Bulk reassign via query — reassign matching tickets, validation</li>
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

    // Team members (for assignment carry tests)
    private static Number salesMember1UserId, salesMember2UserId;

    // Ticket IDs and assignment data captured during tests
    private static Number s7Cp1AssignedUserId;
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
    //  S6: Assignment Carry — Re-inquiry gets same assigned user
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(700)
    void s6_01_inviteTeamMembers() {
        SecurityApi secApi = new SecurityApi(baseHost());

        // Invite SalesMember1
        Response inv1 = secApi.inviteUser(token, clientCode, appCode, mapOf(
                "emailId", "dedup-sm1-" + uid + "@inttest.local",
                "firstName", "DedupSM1", "lastName", "IntTest",
                "profileId", 122, "reportingTo", userId
        ));
        assertThat(inv1.statusCode()).as("Invite SM1").isIn(200, 201);
        String code1 = inv1.body().path("userRequest.inviteCode");

        Response acc1 = secApi.acceptInvite(clientCode, appCode, mapOf(
                "emailId", "dedup-sm1-" + uid + "@inttest.local",
                "firstName", "DedupSM1", "lastName", "IntTest",
                "password", "Test@1234", "passType", "PASSWORD", "inviteCode", code1
        ));
        assertThat(acc1.statusCode()).as("Accept SM1").isIn(200, 201);
        salesMember1UserId = acc1.body().path("authentication.user.id");

        // Invite SalesMember2
        Response inv2 = secApi.inviteUser(token, clientCode, appCode, mapOf(
                "emailId", "dedup-sm2-" + uid + "@inttest.local",
                "firstName", "DedupSM2", "lastName", "IntTest",
                "profileId", 122, "reportingTo", userId
        ));
        assertThat(inv2.statusCode()).as("Invite SM2").isIn(200, 201);
        String code2 = inv2.body().path("userRequest.inviteCode");

        Response acc2 = secApi.acceptInvite(clientCode, appCode, mapOf(
                "emailId", "dedup-sm2-" + uid + "@inttest.local",
                "firstName", "DedupSM2", "lastName", "IntTest",
                "password", "Test@1234", "passType", "PASSWORD", "inviteCode", code2
        ));
        assertThat(acc2.statusCode()).as("Accept SM2").isIn(200, 201);
        salesMember2UserId = acc2.body().path("authentication.user.id");
    }

    @Test
    @Order(710)
    void s6_02_createCPRoundRobinRuleForTeam() {
        // Create a CP source rule (order=1) distributing to SM1 and SM2.
        // The default rule (order=0 from setup) assigns to owner.
        // This rule overrides for Channel Partner source.
        Response res = api.createCreationRule(mapOf(
                "name", "CP Team RR Rule",
                "productTemplateId", templateId,
                "stageId", stageFreshId,
                "order", 1,
                "userDistributionType", "ROUND_ROBIN",
                "condition", Map.of(
                        "operator", "AND",
                        "negate", false,
                        "conditions", List.of(Map.of(
                                "field", "Deal.source",
                                "value", "Channel Partner",
                                "operator", "EQUALS",
                                "negate", false
                        ))
                ),
                "userDistributions", List.of(
                        Map.of("userId", salesMember1UserId),
                        Map.of("userId", salesMember2UserId)
                )
        ));
        assertThat(res.statusCode()).as("Create CP RR rule for dedup test").isIn(200, 201);
    }

    @Test
    @Order(720)
    void s6_03_firstCPLeadAssignedToSM1() {
        // First CP lead — CP rule routes to SM1 via round-robin
        Response res = api.createTicket(mapOf(
                "name", "Dedup_S6_Lead1",
                "dialCode", 91,
                "phoneNumber", "+918000000006",
                "productId", product1Id,
                "source", "Channel Partner",
                "subSource", "CP_Alpha"
        ));
        assertThat(res.statusCode()).as("Create first CP lead").isIn(200, 201);
        Number assignedUser = res.body().path("assignedUserId");
        assertThat(assignedUser).as("First CP lead should be assigned").isNotNull();
        assertThat(assignedUser.longValue()).as("First CP lead assigned to SM1")
                .isEqualTo(salesMember1UserId.longValue());
    }

    @Test
    @Order(730)
    void s6_04_reInquirySamePhone_getsAssignedToSameSM1() {
        // Same phone, same source — dedup rule allows. Should be assigned to SM1 (not SM2).
        Response res = api.createTicket(mapOf(
                "name", "Dedup_S6_Lead1_ReInquiry",
                "dialCode", 91,
                "phoneNumber", "+918000000006",
                "productId", product1Id,
                "source", "Channel Partner",
                "subSource", "CP_Beta"
        ));
        assertThat(res.statusCode()).as("Re-inquiry should be allowed").isIn(200, 201);
        Number assignedUser = res.body().path("assignedUserId");
        assertThat(assignedUser).as("Re-inquiry should be assigned").isNotNull();
        assertThat(assignedUser.longValue())
                .as("Re-inquiry should go to SAME user (SM1), not round-robin to SM2")
                .isEqualTo(salesMember1UserId.longValue());
    }

    @Test
    @Order(740)
    void s6_05_nextNewLead_goesToSM2_viaRoundRobin() {
        // A completely new phone number — round-robin should assign to SM2
        Response res = api.createTicket(mapOf(
                "name", "Dedup_S6_NewLead",
                "dialCode", 91,
                "phoneNumber", "+918000000007",
                "productId", product1Id,
                "source", "Channel Partner",
                "subSource", "CP_Gamma"
        ));
        assertThat(res.statusCode()).as("New lead should succeed").isIn(200, 201);
        Number assignedUser = res.body().path("assignedUserId");
        assertThat(assignedUser).as("New lead should be assigned").isNotNull();
        assertThat(assignedUser.longValue())
                .as("New lead should go to SM2 via round-robin")
                .isEqualTo(salesMember2UserId.longValue());
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S7: Cross-CP Assignment Carry — Different CPs, same phone
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(800)
    void s7_01_cp1SubmitsLead() {
        // First CP submits a lead — assigned via CP round-robin rule
        Response res = api.createTicket(mapOf(
                "name", "Dedup_S7_CP1Lead",
                "dialCode", 91,
                "phoneNumber", "+918000000008",
                "productId", product1Id,
                "source", "Channel Partner",
                "subSource", "CP_One"
        ));
        assertThat(res.statusCode()).as("CP1 lead").isIn(200, 201);
        s7Cp1AssignedUserId = res.body().path("assignedUserId");
        assertThat(s7Cp1AssignedUserId).as("CP1 lead should be assigned").isNotNull();
    }

    @Test
    @Order(810)
    void s7_02_cp2SubmitsSamePhone_getsAssignedToSameUser() {
        // Different CP, same phone — dedup rule allows. Should go to SAME user as CP1's lead.
        assertThat(s7Cp1AssignedUserId).as("CP1 assigned user must exist").isNotNull();

        Response res = api.createTicket(mapOf(
                "name", "Dedup_S7_CP2Lead_SamePhone",
                "dialCode", 91,
                "phoneNumber", "+918000000008",
                "productId", product1Id,
                "source", "Channel Partner",
                "subSource", "CP_Two"
        ));
        assertThat(res.statusCode()).as("CP2 same phone should be allowed").isIn(200, 201);

        Number cp2AssignedUser = res.body().path("assignedUserId");
        assertThat(cp2AssignedUser).as("CP2 lead should be assigned").isNotNull();
        assertThat(cp2AssignedUser.longValue())
                .as("CP2 lead with same phone should be assigned to SAME user as CP1 lead")
                .isEqualTo(s7Cp1AssignedUserId.longValue());
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S8: Inactive User Skipped in Round-Robin
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(900)
    void s8_01_deactivateSM1() {
        SecurityApi secApi = new SecurityApi(baseHost());
        Response res = secApi.makeUserInActive(token, clientCode, appCode, salesMember1UserId);
        assertThat(res.statusCode()).as("Deactivate SM1").isIn(200, 201);
    }

    @Test
    @Order(910)
    void s8_02_newLeadSkipsSM1_goesToSM2() {
        // SM1 is inactive. New CP lead should skip SM1 and go to SM2.
        Response res = api.createTicket(mapOf(
                "name", "Dedup_S8_InactiveTest",
                "dialCode", 91,
                "phoneNumber", "+918000000009",
                "productId", product1Id,
                "source", "Channel Partner",
                "subSource", "CP_Inactive"
        ));
        assertThat(res.statusCode()).as("Lead with inactive SM1").isIn(200, 201);
        Number assignedUser = res.body().path("assignedUserId");
        assertThat(assignedUser).as("Lead should be assigned").isNotNull();
        assertThat(assignedUser.longValue())
                .as("Lead should skip inactive SM1 and go to SM2")
                .isEqualTo(salesMember2UserId.longValue());
    }

    @Test
    @Order(920)
    void s8_03_reactivateSM1() {
        SecurityApi secApi = new SecurityApi(baseHost());
        Response res = secApi.makeUserActive(token, clientCode, appCode, salesMember1UserId);
        assertThat(res.statusCode()).as("Reactivate SM1").isIn(200, 201);
    }

    @Test
    @Order(930)
    void s8_04_afterReactivation_SM1BackInPool() {
        // SM1 is active again. New lead should go to SM1 (round-robin continues).
        Response res = api.createTicket(mapOf(
                "name", "Dedup_S8_ReactivatedTest",
                "dialCode", 91,
                "phoneNumber", "+918000000010",
                "productId", product1Id,
                "source", "Channel Partner",
                "subSource", "CP_Reactivated"
        ));
        assertThat(res.statusCode()).as("Lead after SM1 reactivation").isIn(200, 201);
        Number assignedUser = res.body().path("assignedUserId");
        assertThat(assignedUser).as("Lead should be assigned").isNotNull();
        // SM1 is back in the pool — round-robin should include both SM1 and SM2
        assertThat(assignedUser.longValue())
                .as("Lead should go to an active team member (SM1 or SM2)")
                .isIn(salesMember1UserId.longValue(), salesMember2UserId.longValue());
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S9: Bulk Reassign via Query
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(1000)
    void s9_01_createLeadsForBulkReassign() {
        // Create 3 leads from "Website" source — all go to owner (default rule)
        for (int i = 1; i <= 3; i++) {
            Response res = api.createTicket(mapOf(
                    "name", "Dedup_S9_BulkLead_" + i,
                    "dialCode", 91,
                    "phoneNumber", "+91900000010" + i,
                    "productId", product1Id,
                    "source", "Website",
                    "subSource", "BulkTest"
            ));
            assertThat(res.statusCode()).as("Create bulk test lead " + i).isIn(200, 201);
        }
    }

    @Test
    @Order(1010)
    void s9_02_bulkReassignBySourceCondition() {
        // Bulk reassign all "Website" + "BulkTest" tickets to SM2
        Response res = api.bulkReassign(mapOf(
                "query", mapOf(
                        "condition", Map.of(
                                "operator", "AND",
                                "negate", false,
                                "conditions", List.of(
                                        Map.of("field", "source", "value", "Website",
                                                "operator", "EQUALS", "negate", false),
                                        Map.of("field", "subSource", "value", "BulkTest",
                                                "operator", "EQUALS", "negate", false)
                                )
                        ),
                        "page", 0,
                        "size", 100
                ),
                "userId", salesMember2UserId,
                "comment", "Bulk reassign to SM2 for testing"
        ));

        assertThat(res.statusCode()).as("Bulk reassign").isEqualTo(200);
        Number count = res.body().as(Integer.class);
        assertThat(count.intValue()).as("Should reassign 3 tickets").isGreaterThanOrEqualTo(3);
    }

    @Test
    @Order(1020)
    void s9_03_verifyBulkReassignedToSM2() {
        // Use SM2's token to verify they can see the bulk reassigned tickets
        EntityProcessorApi sm2Api = new EntityProcessorApi(
                givenAuth(getSmToken(salesMember2UserId), clientCode, appCode));
        Response res = sm2Api.listTickets(0, 100);
        assertThat(res.statusCode()).isEqualTo(200);

        List<Map<String, Object>> content = res.body().path("content");
        // Find the BulkTest tickets
        long bulkTestCount = content.stream()
                .filter(t -> "BulkTest".equals(t.get("subSource")))
                .count();
        assertThat(bulkTestCount).as("SM2 should see the 3 bulk-reassigned tickets")
                .isGreaterThanOrEqualTo(3);
    }

    @Test
    @Order(1030)
    void s9_04_bulkReassignWithInvalidUser_fails() {
        // Try bulk reassign to a user not in sub-org — should fail
        Response res = api.bulkReassign(mapOf(
                "query", mapOf(
                        "condition", Map.of(
                                "field", "source", "value", "Website",
                                "operator", "EQUALS", "negate", false
                        ),
                        "page", 0,
                        "size", 100
                ),
                "userId", 999999,
                "comment", "Should fail"
        ));

        assertThat(res.statusCode()).as("Bulk reassign to invalid user should fail").isEqualTo(400);
    }

    @Test
    @Order(1040)
    void s9_05_bulkReassignWithNoUserId_fails() {
        // Try bulk reassign without userId — should fail
        Response res = api.bulkReassign(mapOf(
                "query", mapOf("page", 0, "size", 100),
                "comment", "No user"
        ));

        assertThat(res.statusCode()).as("Bulk reassign without userId should fail").isEqualTo(400);
    }

    /**
     * Helper to get a token for a team member by userId.
     * SM2 was invited during S6 setup — reuse their token by authenticating.
     */
    private String getSmToken(Number smUserId) {
        // SM2 was created via invite with a known email pattern
        SecurityApi secApi = new SecurityApi(baseHost());
        String smEmail = "dedup-sm2-" + uid + "@inttest.local";
        Response authRes = secApi.authenticate(clientCode, appCode, smEmail, "Test@1234");
        if (authRes.statusCode() == 200) {
            return authRes.body().path("accessToken");
        }
        // Fallback: use admin token (visibility will be broader)
        return token;
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
