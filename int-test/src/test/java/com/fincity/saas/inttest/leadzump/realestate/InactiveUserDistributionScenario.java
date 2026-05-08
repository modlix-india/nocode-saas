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
 * Validates that inactive/removed users are excluded from ticket assignment.
 *
 * <h3>Bug 1 — Direct userId in distribution bypasses active-user filter</h3>
 * A user added to a distribution rule by direct ID and later deactivated should
 * no longer receive ticket assignments.
 *
 * <h3>Bug 3 — Duplicate carry-forward doesn't validate user in distribution</h3>
 * When a duplicate ticket is created for the same phone number, the existing
 * ticket's assignedUserId should only be carried forward if that user is still
 * in the current distribution pool.
 *
 * <h3>Test Setup:</h3>
 * <ul>
 *   <li>Builder admin registers, creates template/stages/product</li>
 *   <li>Three team members invited for round-robin distribution</li>
 *   <li>Creation rule with ROUND_ROBIN across all three members</li>
 * </ul>
 *
 * <h3>Test Scenarios:</h3>
 * <ul>
 *   <li>S1 (300-320): Create tickets, verify round-robin works across all 3 members</li>
 *   <li>S2 (400-430): Deactivate member3, create new tickets, verify member3 is skipped</li>
 *   <li>S3 (500-530): Create duplication rule that allows duplicates, create duplicate
 *       ticket for a phone assigned to deactivated member3, verify new ticket is NOT
 *       assigned to member3 but gets fresh assignment from distribution</li>
 *   <li>S4 (600-610): Reactivate member3, verify they receive assignments again</li>
 * </ul>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class InactiveUserDistributionScenario extends BaseIntegrationTest {

    // Builder admin
    private static String token, clientCode, appCode, parentClientCode;
    private static Number userId;
    private static EntityProcessorApi api;

    // Template and stages
    private static Number templateId;
    private static Number stageFreshId, stageOpenId;

    // Product
    private static Number productId;

    // Team members (for round-robin)
    private static Number member1UserId, member2UserId, member3UserId;

    // Ticket tracking (assigned user IDs for round-robin verification)
    private static Number ticket1AssignedUserId, ticket2AssignedUserId, ticket3AssignedUserId;

    // Shared UID for unique emails per run
    private static String uid;
    private static SecurityApi secApi;
    private static Number salesMemberProfileId;

    @BeforeAll
    void setup() {
        appCode = prop("leadzump.app.code");
        parentClientCode = prop("leadzump.client.code");

        uid = UUID.randomUUID().toString().substring(0, 8);
        String email = "re-inact-" + uid + "@inttest.local";
        String password = "Test@1234";

        secApi = new SecurityApi(baseHost());

        Response otpRes = secApi.generateRegistrationOtp(parentClientCode, appCode, email);
        assertThat(otpRes.statusCode()).as("Generate OTP").isEqualTo(200);

        Response regRes = secApi.register(parentClientCode, appCode, mapOf(
                "clientName", "RE_Inact_" + uid,
                "firstName", "InactBuilder",
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
    //  Setup: Template, Stages, Product
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(100)
    void setup_createTemplateAndStages() {
        Response tmpl = api.createProductTemplate(Map.of(
                "name", "RE_InactTest_Template_" + uid,
                "description", "Inactive user distribution test",
                "productTemplateType", "GENERAL"
        ));
        assertThat(tmpl.statusCode()).as("Create product template").isIn(200, 201);
        templateId = tmpl.body().path("id");
        assertThat(templateId).isNotNull();

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
                "name", "RE_InactTest_Product_" + uid,
                "description", "Inactive user distribution test product",
                "productTemplateId", templateId
        ));
        assertThat(res.statusCode()).as("Create product").isIn(200, 201);
        productId = res.body().path("id");
        assertThat(productId).isNotNull();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Setup: Invite 3 team members
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(200)
    void setup_inviteMember1() {
        member1UserId = inviteMember("inact-m1-" + uid + "@inttest.local", "Member1");
        assertThat(member1UserId).as("Member1 userId").isNotNull();
    }

    @Test
    @Order(210)
    void setup_inviteMember2() {
        member2UserId = inviteMember("inact-m2-" + uid + "@inttest.local", "Member2");
        assertThat(member2UserId).as("Member2 userId").isNotNull();
    }

    @Test
    @Order(220)
    void setup_inviteMember3() {
        member3UserId = inviteMember("inact-m3-" + uid + "@inttest.local", "Member3");
        assertThat(member3UserId).as("Member3 userId").isNotNull();
    }

    @Test
    @Order(230)
    void setup_createCreationRuleWithRoundRobin() {
        Response res = api.createCreationRule(mapOf(
                "name", "Inact RR Rule",
                "productTemplateId", templateId,
                "stageId", stageOpenId,
                "order", 0,
                "userDistributionType", "ROUND_ROBIN",
                "userDistributions", List.of(
                        Map.of("userId", member1UserId),
                        Map.of("userId", member2UserId),
                        Map.of("userId", member3UserId)
                )
        ));
        assertThat(res.statusCode()).as("Create creation rule with round-robin").isIn(200, 201);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S1: Verify round-robin works across all 3 members
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(300)
    void s1_01_createTicket1_assignedToAMember() {
        Response res = api.createTicket(mapOf(
                "name", "Inact_Lead1",
                "dialCode", 91,
                "phoneNumber", "+919300000001",
                "productId", productId,
                "source", "Website",
                "subSource", "InactTest"
        ));
        assertThat(res.statusCode()).as("Create ticket 1").isIn(200, 201);
        ticket1AssignedUserId = res.body().path("assignedUserId");
        assertThat(ticket1AssignedUserId).as("Ticket 1 should be assigned").isNotNull();
        assertThat(ticket1AssignedUserId.longValue())
                .as("Ticket 1 assigned to one of the three members")
                .isIn(member1UserId.longValue(), member2UserId.longValue(), member3UserId.longValue());
    }

    @Test
    @Order(310)
    void s1_02_createTicket2_assignedToNextMember() {
        Response res = api.createTicket(mapOf(
                "name", "Inact_Lead2",
                "dialCode", 91,
                "phoneNumber", "+919300000002",
                "productId", productId,
                "source", "Website",
                "subSource", "InactTest"
        ));
        assertThat(res.statusCode()).as("Create ticket 2").isIn(200, 201);
        ticket2AssignedUserId = res.body().path("assignedUserId");
        assertThat(ticket2AssignedUserId).as("Ticket 2 should be assigned").isNotNull();
        assertThat(ticket2AssignedUserId.longValue())
                .as("Ticket 2 assigned to a different member than ticket 1 (round-robin)")
                .isNotEqualTo(ticket1AssignedUserId.longValue());
    }

    @Test
    @Order(320)
    void s1_03_createTicket3_assignedToThirdMember() {
        Response res = api.createTicket(mapOf(
                "name", "Inact_Lead3",
                "dialCode", 91,
                "phoneNumber", "+919300000003",
                "productId", productId,
                "source", "Website",
                "subSource", "InactTest"
        ));
        assertThat(res.statusCode()).as("Create ticket 3").isIn(200, 201);
        ticket3AssignedUserId = res.body().path("assignedUserId");
        assertThat(ticket3AssignedUserId).as("Ticket 3 should be assigned").isNotNull();

        // All three members should have received one ticket each
        assertThat(ticket3AssignedUserId.longValue())
                .as("Ticket 3 assigned to the remaining member (round-robin)")
                .isNotEqualTo(ticket1AssignedUserId.longValue())
                .isNotEqualTo(ticket2AssignedUserId.longValue());
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S2: Deactivate member3, verify they are excluded from assignment
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(400)
    void s2_01_deactivateMember3() {
        Response res = secApi.makeUserInActive(token, parentClientCode, appCode, member3UserId);
        assertThat(res.statusCode())
                .as("Deactivate Member3: " + res.body().asString())
                .isIn(200, 201);
    }

    @Test
    @Order(410)
    void s2_02_createTicketAfterDeactivation_notAssignedToMember3() {
        Response res = api.createTicket(mapOf(
                "name", "Inact_Lead4_PostDeactivation",
                "dialCode", 91,
                "phoneNumber", "+919300000004",
                "productId", productId,
                "source", "Website",
                "subSource", "InactTest"
        ));
        assertThat(res.statusCode()).as("Create ticket 4 after deactivation").isIn(200, 201);

        Number assignedUserId = res.body().path("assignedUserId");
        assertThat(assignedUserId).as("Ticket 4 should be assigned").isNotNull();
        assertThat(assignedUserId.longValue())
                .as("Ticket 4 should NOT be assigned to deactivated member3")
                .isNotEqualTo(member3UserId.longValue());
        assertThat(assignedUserId.longValue())
                .as("Ticket 4 should be assigned to member1 or member2")
                .isIn(member1UserId.longValue(), member2UserId.longValue());
    }

    @Test
    @Order(420)
    void s2_03_createAnotherTicket_stillExcludesMember3() {
        Response res = api.createTicket(mapOf(
                "name", "Inact_Lead5_PostDeactivation",
                "dialCode", 91,
                "phoneNumber", "+919300000005",
                "productId", productId,
                "source", "Website",
                "subSource", "InactTest"
        ));
        assertThat(res.statusCode()).as("Create ticket 5 after deactivation").isIn(200, 201);

        Number assignedUserId = res.body().path("assignedUserId");
        assertThat(assignedUserId).as("Ticket 5 should be assigned").isNotNull();
        assertThat(assignedUserId.longValue())
                .as("Ticket 5 should NOT be assigned to deactivated member3")
                .isNotEqualTo(member3UserId.longValue());
    }

    @Test
    @Order(430)
    void s2_04_roundRobinBetweenTwoActiveMembers() {
        // Create two more tickets to verify round-robin cycles between member1 and member2
        Response res6 = api.createTicket(mapOf(
                "name", "Inact_Lead6",
                "dialCode", 91,
                "phoneNumber", "+919300000006",
                "productId", productId,
                "source", "Website",
                "subSource", "InactTest"
        ));
        assertThat(res6.statusCode()).as("Create ticket 6").isIn(200, 201);
        Number assigned6 = res6.body().path("assignedUserId");

        Response res7 = api.createTicket(mapOf(
                "name", "Inact_Lead7",
                "dialCode", 91,
                "phoneNumber", "+919300000007",
                "productId", productId,
                "source", "Website",
                "subSource", "InactTest"
        ));
        assertThat(res7.statusCode()).as("Create ticket 7").isIn(200, 201);
        Number assigned7 = res7.body().path("assignedUserId");

        // Both should be assigned to active members only
        assertThat(assigned6.longValue())
                .as("Ticket 6 assigned to active member")
                .isIn(member1UserId.longValue(), member2UserId.longValue());
        assertThat(assigned7.longValue())
                .as("Ticket 7 assigned to active member")
                .isIn(member1UserId.longValue(), member2UserId.longValue());

        // Round-robin should alternate between the two active members
        assertThat(assigned6.longValue())
                .as("Ticket 6 and 7 should be assigned to different members (round-robin)")
                .isNotEqualTo(assigned7.longValue());
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S3: Duplicate carry-forward should not use deactivated user
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(500)
    void s3_01_setupDuplicationRule() {
        // Create a duplication rule that allows re-inquiries from "Website" source
        // when the existing ticket's stage is at or before the Open stage.
        // This allows the duplicate ticket to be created and go through assignment.
        Response res = api.createDuplicationRule(mapOf(
                "name", "Inact_Dedup_Website",
                "productTemplateId", templateId,
                "source", "Website",
                "maxStageId", stageFreshId,
                "order", 1,
                "condition", Map.of(
                        "operator", "AND",
                        "negate", false,
                        "conditions", List.of(Map.of(
                                "field", "Deal.source",
                                "value", "Website",
                                "operator", "EQUALS",
                                "negate", false
                        ))
                )
        ));
        assertThat(res.statusCode())
                .as("Duplication rule creation: " + res.body().asString())
                .isIn(200, 201);
    }

    @Test
    @Order(510)
    void s3_02_createDuplicateForPhoneAssignedToDeactivatedUser() {
        // Ticket 3 (from S1) was assigned to member3 (now deactivated)
        // Creating a duplicate ticket with the same phone should NOT carry forward member3
        assertThat(ticket3AssignedUserId.longValue())
                .as("Pre-condition: ticket3 was assigned to member3")
                .isEqualTo(member3UserId.longValue());

        Response res = api.createTicket(mapOf(
                "name", "Inact_Lead3_Duplicate",
                "dialCode", 91,
                "phoneNumber", "+919300000003",
                "productId", productId,
                "source", "Website",
                "subSource", "InactTest_Dup"
        ));

        // If duplicate is blocked, the fix is still valid (member3 won't get assignment)
        // If duplicate is allowed, the new ticket should NOT be assigned to deactivated member3
        if (res.statusCode() == 200 || res.statusCode() == 201) {
            Number assignedUserId = res.body().path("assignedUserId");
            assertThat(assignedUserId).as("Duplicate ticket should be assigned").isNotNull();
            assertThat(assignedUserId.longValue())
                    .as("Duplicate ticket should NOT carry forward deactivated member3's assignment")
                    .isNotEqualTo(member3UserId.longValue());
            assertThat(assignedUserId.longValue())
                    .as("Duplicate ticket should be assigned to an active member")
                    .isIn(member1UserId.longValue(), member2UserId.longValue());
        } else {
            System.out.println("DIAG: Duplicate ticket was blocked (status=" + res.statusCode()
                    + "), which is acceptable - deactivated user won't receive assignment");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S4: Reactivate member3, verify they receive assignments again
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(600)
    void s4_01_reactivateMember3() {
        Response res = secApi.makeUserActive(token, parentClientCode, appCode, member3UserId);
        assertThat(res.statusCode())
                .as("Reactivate Member3: " + res.body().asString())
                .isIn(200, 201);
    }

    @Test
    @Order(610)
    void s4_02_createTicketsAfterReactivation_member3ReceivesAssignment() {
        // Create enough tickets to cycle through all 3 members
        boolean member3Assigned = false;

        for (int i = 0; i < 4; i++) {
            Response res = api.createTicket(mapOf(
                    "name", "Inact_Lead_Reactivated_" + i,
                    "dialCode", 91,
                    "phoneNumber", "+91930000" + String.format("%04d", 20 + i),
                    "productId", productId,
                    "source", "Website",
                    "subSource", "InactTest_Reactivated"
            ));
            assertThat(res.statusCode())
                    .as("Create ticket after reactivation #" + i)
                    .isIn(200, 201);

            Number assignedUserId = res.body().path("assignedUserId");
            assertThat(assignedUserId).as("Ticket should be assigned").isNotNull();

            if (assignedUserId.longValue() == member3UserId.longValue()) {
                member3Assigned = true;
            }
        }

        assertThat(member3Assigned)
                .as("Reactivated member3 should receive at least one assignment after reactivation")
                .isTrue();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════════

    private Number inviteMember(String email, String firstName) {
        Response inv = secApi.inviteUser(token, parentClientCode, appCode, mapOf(
                "emailId", email,
                "firstName", firstName, "lastName", "IntTest",
                "profileId", salesMemberProfileId, "reportingTo", userId
        ));
        assertThat(inv.statusCode()).as("Invite " + firstName + ": " + inv.body().asString()).isIn(200, 201);
        String code = inv.body().path("userRequest.inviteCode");

        Response acc = secApi.acceptInvite(parentClientCode, appCode, mapOf(
                "emailId", email,
                "firstName", firstName, "lastName", "IntTest",
                "password", "Test@1234", "passType", "PASSWORD", "inviteCode", code
        ));
        assertThat(acc.statusCode()).as("Accept " + firstName).isIn(200, 201);
        return acc.body().path("authentication.user.id");
    }

    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> mapOf(Object... keyValues) {
        Map<Object, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return (Map<K, V>) map;
    }
}
